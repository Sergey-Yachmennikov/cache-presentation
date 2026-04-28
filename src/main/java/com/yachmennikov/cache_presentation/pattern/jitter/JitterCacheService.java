package com.yachmennikov.cache_presentation.pattern.jitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yachmennikov.cache_presentation.strategy.cacheaside.Product;
import com.yachmennikov.cache_presentation.strategy.cacheaside.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Демонстрация TTL + Jitter — защита от Cache Stampede (Thundering Herd).
 *
 * Проблема без jitter:
 *   Если 1000 продуктов закешированы с одинаковым TTL=60 сек,
 *   через 60 сек они все протухают одновременно → 1000 запросов идут в БД разом.
 *
 * Решение — jitter:
 *   К базовому TTL добавляем случайный разброс ±JITTER_SECONDS.
 *   Кеши протухают в разное время → нагрузка на БД размазывается.
 *
 * Пример: BASE_TTL=60, JITTER=10 → реальный TTL = 50..70 сек (равномерно).
 *
 * Используем StringRedisTemplate + ObjectMapper напрямую,
 * так как @Cacheable не поддерживает per-entry TTL с jitter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JitterCacheService {

    private static final String KEY_PREFIX     = "jitter:product:";
    private static final long   BASE_TTL       = 60L;
    private static final long   JITTER_SECONDS = 10L;

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository   repository;
    private final ObjectMapper        objectMapper;

    /**
     * Читает продукт из Redis.
     * При cache miss — идёт в БД и сохраняет с TTL + случайный jitter.
     */
    public Product findById(Long id) {
        String key  = KEY_PREFIX + id;
        String json = redisTemplate.opsForValue().get(key);

        if (json != null) {
            log.info("[CACHE HIT] product {}", id);
            return deserialize(json);
        }

        log.info("[DB] Loading product {} from database", id);
        Product product = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        long ttl = BASE_TTL + ThreadLocalRandom.current().nextLong(-JITTER_SECONDS, JITTER_SECONDS + 1);
        redisTemplate.opsForValue().set(key, serialize(product), ttl, TimeUnit.SECONDS);
        log.info("[CACHE SET] product {} → TTL {} sec (base={}, jitter={})",
                id, ttl, BASE_TTL, ttl - BASE_TTL);

        return product;
    }

    public void evict(Long id) {
        redisTemplate.delete(KEY_PREFIX + id);
        log.info("[CACHE EVICT] product {}", id);
    }

    // ---------------------------------------------------------------

    private String serialize(Product product) {
        try {
            return objectMapper.writeValueAsString(product);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    private Product deserialize(String json) {
        try {
            return objectMapper.readValue(json, Product.class);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
}
