package com.yachmennikov.cache_presentation.service;

import com.yachmennikov.cache_presentation.entity.Product;
import com.yachmennikov.cache_presentation.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cache-Aside (Lazy Loading) стратегия кэширования.
 *
 * Принцип работы:
 * 1. READ  — приложение сначала ищет данные в кэше.
 *            При MISS — загружает из БД и кладёт в кэш.      (@Cacheable)
 * 2. WRITE — приложение пишет в БД, затем обновляет кэш.     (@CachePut)
 * 3. DELETE — приложение удаляет из БД и инвалидирует кэш.   (@CacheEvict)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;

    /**
     * Cache-Aside READ:
     * Spring проверяет Redis по ключу products::{id}.
     * - HIT  → возвращает значение из кэша, БД не вызывается.
     * - MISS → выполняет метод, результат сохраняется в Redis на 60 сек.
     */
    @Cacheable(value = "products", key = "#id")
    public Product findById(Long id) {
        log.info("[DB] Loading product {} from database", id);
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    /**
     * Список продуктов не кэшируется — коллекции быстро устаревают
     * и требуют сложной инвалидации при любом изменении.
     */
    public List<Product> findAll() {
        log.info("[DB] Loading all products from database");
        return repository.findAll();
    }

    /**
     * Cache-Aside WRITE:
     * Сохраняет в БД, затем @CachePut кладёт актуальный объект в Redis.
     */
    @CachePut(value = "products", key = "#result.id")
    public Product save(Product product) {
        Product saved = repository.save(product);
        log.info("[DB] Saved product {}, cache updated", saved.getId());
        return saved;
    }

    /**
     * Cache-Aside UPDATE:
     * Обновляет запись в БД, затем @CachePut кладёт актуальный объект в Redis.
     */
    @CachePut(value = "products", key = "#id")
    public Product update(Long id, Product product) {
        product.setId(id);
        Product updated = repository.save(product);
        log.info("[DB] Updated product {}, cache updated", id);
        return updated;
    }

    /**
     * Cache-Aside DELETE:
     * Удаляет из БД, затем @CacheEvict вытесняет запись из Redis.
     */
    @CacheEvict(value = "products", key = "#id")
    public void delete(Long id) {
        repository.deleteById(id);
        log.info("[DB] Deleted product {}, cache evicted", id);
    }
}
