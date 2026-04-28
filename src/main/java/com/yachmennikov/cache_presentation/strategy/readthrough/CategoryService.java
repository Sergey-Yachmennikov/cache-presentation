package com.yachmennikov.cache_presentation.strategy.readthrough;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-Through стратегия кэширования.
 *
 * Принцип работы:
 * 1. READ  — приложение обращается ТОЛЬКО к кэшу.
 *            При HIT  — кэш возвращает данные.
 *            При MISS — кэш сам загружает данные из БД через CacheLoader
 *                       (определён в ReadThroughCacheConfig) и возвращает их.
 *            Сервис не содержит логики загрузки из БД — за него это делает кэш.
 * 2. WRITE — запись идёт в БД + кэш инвалидируется (@CacheEvict).
 *            Следующий GET будет cache miss — CacheLoader загрузит свежие данные.
 * 3. DELETE — удаление из БД + инвалидация кэша.
 *
 * Отличие от Cache-Aside:
 * - Cache-Aside: логика "при miss — сходи в БД" находится в сервисе (@Cacheable
 *   проксирует вызов метода, который явно читает из repository).
 * - Read-Through: логика загрузки находится в CacheLoader внутри конфигурации кэша.
 *   Метод findById ниже при cache miss НЕ вызывается — вместо него срабатывает CacheLoader.
 *   Приложение взаимодействует только с кэшем, не зная о БД.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;

    /**
     * Read-Through READ:
     * @Cacheable проверяет кэш по ключу categories::{id}.
     * - HIT  → кэш возвращает данные, этот метод не вызывается.
     * - MISS → CacheLoader из ReadThroughCacheConfig загружает данные из БД,
     *          кладёт в кэш и возвращает результат. Этот метод тоже не вызывается.
     *
     * Тело метода — запасной путь на случай если CacheLoader не настроен.
     * В нормальной работе оно недостижимо.
     */
    @Cacheable(value = "categories", key = "#id", cacheManager = "readThroughCacheManager")
    public Category findById(Long id) {
        log.warn("[SERVICE] Fallback: loading category {} directly — CacheLoader did not intercept", id);
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found: " + id));
    }

    public List<Category> findAll() {
        log.info("[DB] Loading all categories from database");
        return repository.findAll();
    }

    public Category save(Category category) {
        Category saved = repository.save(category);
        log.info("[DB] Saved category {}", saved.getId());
        return saved;
    }

    /**
     * Read-Through UPDATE:
     * Обновляем в БД, инвалидируем кэш.
     * Следующий GET даст cache miss — CacheLoader сам загрузит свежие данные из БД.
     */
    @CacheEvict(value = "categories", key = "#id", cacheManager = "readThroughCacheManager")
    public Category update(Long id, Category category) {
        category.setId(id);
        Category updated = repository.save(category);
        log.info("[DB] Updated category {}, cache evicted — CacheLoader will reload on next GET", id);
        return updated;
    }

    @CacheEvict(value = "categories", key = "#id", cacheManager = "readThroughCacheManager")
    public void delete(Long id) {
        repository.deleteById(id);
        log.info("[DB] Deleted category {}, cache evicted", id);
    }
}
