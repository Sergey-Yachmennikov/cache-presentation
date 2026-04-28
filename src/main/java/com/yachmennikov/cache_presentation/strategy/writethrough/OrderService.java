package com.yachmennikov.cache_presentation.strategy.writethrough;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Write-Through стратегия кэширования.
 *
 * Принцип работы:
 * 1. READ  — приложение сначала ищет данные в кэше.
 *            При MISS — загружает из БД и кладёт в кэш.              (@Cacheable)
 * 2. WRITE — приложение пишет одновременно в БД И в кэш.            (@CachePut)
 *            Кэш всегда актуален — cache miss после записи невозможен.
 * 3. DELETE — приложение удаляет из БД и инвалидирует кэш.          (@CacheEvict)
 *
 * Отличие от Cache-Aside:
 * - Cache-Aside: запись → только БД, кэш инвалидируется (@CacheEvict),
 *                следующий GET заполнит кэш через cache miss.
 * - Write-Through: запись → БД + кэш одновременно (@CachePut),
 *                  следующий GET всегда получит HIT.
 *
 * Бизнес-обоснование (заказы):
 * Статус заказа читается очень часто — клиент постоянно проверяет обновления.
 * При смене статуса кэш должен отражать изменение немедленно,
 * иначе клиент увидит устаревший статус до истечения TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;

    /**
     * Write-Through READ:
     * Spring проверяет Redis по ключу orders::{id}.
     * - HIT  → возвращает значение из кэша, БД не вызывается.
     * - MISS → выполняет метод, результат сохраняется в Redis на 60 сек.
     */
    @Cacheable(value = "orders", key = "#id")
    public Order findById(Long id) {
        log.info("[DB] Loading order {} from database", id);
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    public List<Order> findAll() {
        log.info("[DB] Loading all orders from database");
        return repository.findAll();
    }

    /**
     * Write-Through CREATE:
     * Создаёт заказ в БД. Кэш не затрагивается —
     * новый id ещё не закэширован, первый GET заполнит кэш через @Cacheable.
     */
    public Order create(Order order) {
        Order saved = repository.save(order);
        log.info("[DB] Created order {}", saved.getId());
        return saved;
    }

    /**
     * Write-Through UPDATE:
     * Обновляет в БД и одновременно кладёт актуальный объект в Redis (@CachePut).
     * Следующий GET всегда получит HIT с актуальными данными.
     *
     * Ключевое отличие от Cache-Aside:
     * Cache-Aside использовал бы @CacheEvict — кэш сбрасывался бы,
     * и следующий GET шёл бы в БД (cache miss).
     * Write-Through использует @CachePut — кэш обновляется сразу,
     * cache miss после обновления невозможен.
     */
    @CachePut(value = "orders", key = "#id")
    public Order update(Long id, Order order) {
        order.setId(id);
        Order updated = repository.save(order);
        log.info("[DB] Updated order {}, cache updated (write-through)", id);
        return updated;
    }

    /**
     * Write-Through DELETE:
     * Удаляет из БД и инвалидирует кэш.
     */
    @CacheEvict(value = "orders", key = "#id")
    public void delete(Long id) {
        repository.deleteById(id);
        log.info("[DB] Deleted order {}, cache evicted", id);
    }
}
