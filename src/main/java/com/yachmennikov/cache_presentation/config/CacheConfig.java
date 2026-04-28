package com.yachmennikov.cache_presentation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    /**
     * Явно объявляем Redis CacheManager как @Primary.
     * Это необходимо потому, что ReadThroughCacheConfig регистрирует второй бин
     * типа CacheManager (Caffeine), из-за чего Spring Boot auto-configuration
     * для Redis отключается по условию @ConditionalOnMissingBean(CacheManager.class).
     * Без @Primary Spring не знает какой CacheManager использовать по умолчанию
     * в @Cacheable без явного cacheManager.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60));

        // Negative Caching: кеш для купонов разрешает хранить null.
        // TTL намеренно короткий (30 сек) — если купон создадут,
        // через 30 сек null-запись истечёт и следующий GET найдёт объект.
        // @CacheEvict при save() сбрасывает null немедленно, не дожидаясь TTL.
        RedisCacheConfiguration couponsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("coupons", couponsConfig)
                .build();
    }
}
