package com.yachmennikov.cache_presentation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.yachmennikov.cache_presentation.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Конфигурация кэша для Read-Through стратегии.
 *
 * Используется Caffeine — единственный провайдер в экосистеме Spring,
 * поддерживающий LoadingCache (кэш с встроенным загрузчиком данных).
 *
 * Ключевое отличие от Cache-Aside:
 * - Cache-Aside: при cache miss приложение само идёт в БД и заполняет кэш.
 * - Read-Through: при cache miss кэш сам вызывает CacheLoader и загружает данные.
 *   Приложение работает только с кэшем — не знает о существовании БД.
 *
 * CacheLoader определён здесь, в конфигурации кэша — это и есть
 * главное архитектурное отличие Read-Through.
 */
@Slf4j
@Configuration
public class ReadThroughCacheConfig {

    /**
     * Отдельный CacheManager для Read-Through кэша.
     * Не помечаем @Primary — основным остаётся Redis CacheManager,
     * автоконфигурированный Spring Boot для Cache-Aside и Write-Through.
     *
     * CacheLoader (функция загрузки из БД) регистрируется здесь,
     * а не в сервисном слое — в этом суть Read-Through.
     */
    @Bean
    public CacheManager readThroughCacheManager(CategoryRepository repository) {
        CaffeineCacheManager manager = new CaffeineCacheManager("categories");
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(60, TimeUnit.SECONDS)
        );
        manager.setCacheLoader(key -> {
            log.info("[CACHE LOADER] Cache miss — loading category {} from database", key);
            return repository.findById((Long) key)
                    .orElseThrow(() -> new RuntimeException("Category not found: " + key));
        });
        return manager;
    }
}
