package com.yachmennikov.cache_presentation.service;

import com.yachmennikov.cache_presentation.entity.Coupon;
import com.yachmennikov.cache_presentation.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Демонстрация Negative Caching — кеширование отсутствия значения.
 *
 * Проблема без него (Cache Penetration):
 *   Запросы к несуществующим купонам каждый раз доходят до БД,
 *   что позволяет дропнуть БД запросами к заведомо отсутствующим ключам.
 *
 * Решение:
 *   Кешировать null-результат с коротким TTL (30 сек).
 *   Включается через allowCacheNullValues() в CacheConfig.
 *
 * Сценарий работы:
 *   1. GET "GHOST"  → Cache MISS → БД: не найдено → Redis: null (TTL 30s)
 *   2. GET "GHOST"  → Cache HIT  → null из Redis, БД не вызывается
 *   3. POST "GHOST" → запись в БД + @CacheEvict сбрасывает null из Redis
 *   4. GET "GHOST"  → Cache MISS → БД: найдено → Redis: объект (TTL 30s)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository repository;

    /**
     * Negative Caching READ:
     * - HIT (объект)  → возвращает купон из Redis, БД не вызывается.
     * - HIT (null)    → возвращает null из Redis, БД не вызывается.
     *                   Именно это предотвращает Cache Penetration.
     * - MISS          → идёт в БД. Если купона нет — кеширует null.
     *                   Если есть — кеширует объект.
     *
     * null разрешён по умолчанию в RedisCacheConfiguration (см. CacheConfig).
     */
    @Cacheable(value = "coupons", key = "#code")
    public Coupon findByCode(String code) {
        log.info("[DB] Loading coupon '{}' from database", code);
        return repository.findByCode(code).orElse(null);
    }

    /**
     * Negative Caching CREATE:
     * После создания купона сбрасываем закешированный null,
     * чтобы следующий GET нашёл реальный объект в БД.
     */
    @CacheEvict(value = "coupons", key = "#coupon.code")
    public Coupon save(Coupon coupon) {
        Coupon saved = repository.save(coupon);
        log.info("[DB] Saved coupon '{}', null-cache evicted", saved.getCode());
        return saved;
    }

    /**
     * Negative Caching DELETE:
     * Удаляем из БД и сбрасываем кеш — следующий GET закеширует null заново.
     */
    @CacheEvict(value = "coupons", key = "#code")
    public void delete(String code) {
        repository.findByCode(code).ifPresent(c -> {
            repository.delete(c);
            log.info("[DB] Deleted coupon '{}', cache evicted", code);
        });
    }
}
