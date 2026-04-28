package com.yachmennikov.cache_presentation.strategy.writebehind;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Write-Behind (Write-Back) стратегия кэширования.
 *
 * Принцип работы:
 * 1. READ  — приложение сначала ищет данные в кэше.
 *            При MISS — загружает из БД и кладёт в кэш.              (@Cacheable)
 * 2. WRITE — приложение пишет ТОЛЬКО в кэш и возвращает ответ сразу.
 *            ID записи помещается в "dirty queue".
 *            Шедулер периодически сбрасывает грязные записи в БД.
 * 3. DELETE — удаляет из кэша сразу; если запись ещё не сброшена в БД,
 *             убирает её из очереди и сразу удаляет из БД.
 *
 * Отличие от Write-Through:
 * - Write-Through: запись → БД + кэш одновременно (@CachePut), ответ после записи в БД.
 * - Write-Behind:  запись → только кэш, ответ немедленно; БД обновляется асинхронно.
 *
 * Бизнес-обоснование (уведомления):
 * Уведомления создаются и читаются очень часто (смена статуса is_read).
 * Небольшое отставание БД от кэша допустимо —
 * важна скорость записи, а не немедленная персистентность.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final CacheManager cacheManager;

    /**
     * Очередь id записей, изменённых в кэше, но ещё не сохранённых в БД.
     * ConcurrentLinkedQueue — потокобезопасна для одновременных записей и flush-а.
     */
    private final Queue<String> dirtyQueue = new ConcurrentLinkedQueue<>();

    /**
     * Write-Behind READ:
     * Spring проверяет Redis по ключу notifications::{id}.
     * - HIT  → возвращает значение из кэша, БД не вызывается.
     * - MISS → выполняет метод, результат сохраняется в Redis на 60 сек.
     */
    @Cacheable(value = "notifications", key = "#id")
    public Notification findById(String id) {
        log.info("[DB] Loading notification {} from database", id);
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
    }

    public List<Notification> findAll() {
        log.info("[DB] Loading all notifications from database");
        return repository.findAll();
    }

    /**
     * Write-Behind CREATE:
     * Генерируем UUID в приложении (не ждём БД), кладём в кэш немедленно.
     * ID уходит в dirty queue — шедулер сохранит запись в БД позже.
     */
    public Notification create(Notification notification) {
        notification.setId(UUID.randomUUID().toString());
        notification.setCreatedAt(LocalDateTime.now());

        getCache().put(notification.getId(), notification);
        dirtyQueue.add(notification.getId());

        log.info("[CACHE] Notification {} written to cache, DB write deferred", notification.getId());
        return notification;
    }

    /**
     * Write-Behind UPDATE:
     * Обновляем кэш немедленно, ставим в dirty queue.
     * БД обновится при следующем flush-е шедулера.
     * Клиент получает ответ не дожидаясь записи в БД.
     */
    public Notification update(String id, Notification notification) {
        notification.setId(id);

        getCache().put(id, notification);
        dirtyQueue.add(id);

        log.info("[CACHE] Notification {} updated in cache, DB write deferred", id);
        return notification;
    }

    /**
     * Write-Behind DELETE:
     * Немедленно вытесняем из кэша.
     * Если запись ещё не сброшена в БД — она могла там и не появиться,
     * поэтому идём в БД напрямую (deleteById игнорирует несуществующую запись).
     */
    @CacheEvict(value = "notifications", key = "#id")
    public void delete(String id) {
        dirtyQueue.remove(id);
        repository.deleteById(id);
        log.info("[DB] Deleted notification {}, cache evicted", id);
    }

    /**
     * Периодический сброс грязных записей в БД (flush).
     * Запускается каждые 5 секунд.
     *
     * Алгоритм:
     * 1. Забираем все id из dirty queue атомарно.
     * 2. Для каждого id читаем актуальное состояние из кэша.
     * 3. Сохраняем в БД через repository.save() (INSERT или UPDATE).
     */
    @Scheduled(fixedDelay = 5_000)
    public void flushDirtyEntriesToDatabase() {
        Set<String> toFlush = new HashSet<>();
        String id;
        while ((id = dirtyQueue.poll()) != null) {
            toFlush.add(id);
        }

        if (toFlush.isEmpty()) {
            return;
        }

        log.info("[FLUSH] Flushing {} dirty notification(s) to database", toFlush.size());

        Cache cache = getCache();
        List<Notification> batch = toFlush.stream()
                .map(cache::get)
                .filter(wrapper -> {
                    if (wrapper == null) {
                        log.warn("[FLUSH] A notification expired from cache before flush, skipping");
                    }
                    return wrapper != null;
                })
                .map(wrapper -> (Notification) wrapper.get())
                .toList();

        repository.saveAll(batch);
        log.info("[DB] Flushed {} notification(s) to database in one batch", batch.size());
    }

    private Cache getCache() {
        return cacheManager.getCache("notifications");
    }
}
