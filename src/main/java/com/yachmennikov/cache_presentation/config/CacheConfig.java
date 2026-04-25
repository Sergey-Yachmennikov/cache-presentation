package com.yachmennikov.cache_presentation.config;

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
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
