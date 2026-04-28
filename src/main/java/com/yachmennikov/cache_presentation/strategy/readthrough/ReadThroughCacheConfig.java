package com.yachmennikov.cache_presentation.strategy.readthrough;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация кэша для Read-Through стратегии на базе Hazelcast.
 *
 * Архитектура: client-server.
 * - Сервер: Hazelcast-нода в Docker (localhost:5701), хранит данные.
 * - Клиент: Spring-приложение подключается через HazelcastClient.
 *
 * Почему не MapLoader (стандартный Hazelcast Read-Through)?
 * MapLoader — серверная концепция: логика загрузки данных выполняется
 * на ноде-владельце партиции. В client-server режиме сервер (Docker)
 * не знает о Spring-контексте и репозиториях. Поэтому логику Read-Through
 * реализуем на стороне клиента в ReadThroughHazelcastCache.
 *
 * Ключевое отличие от Cache-Aside остаётся в силе:
 * логика загрузки из БД находится в слое кэша (CategoryMapLoader),
 * а не в сервисном слое — CategoryService работает только с кэшем.
 */
@Slf4j
@Configuration
public class ReadThroughCacheConfig {

    /**
     * Hazelcast-клиент, подключающийся к серверу в Docker.
     * destroyMethod = "shutdown" гарантирует корректное закрытие при остановке приложения.
     */
    @Bean(destroyMethod = "shutdown")
    public HazelcastInstance hazelcastClient() {
        ClientConfig config = new ClientConfig();
        config.setClusterName("cache-cluster");
        config.getNetworkConfig().addAddress("localhost:5701");
        log.info("[HAZELCAST] Connecting to Hazelcast cluster at localhost:5701");
        return HazelcastClient.newHazelcastClient(config);
    }

    /**
     * Отдельный CacheManager для Read-Through кэша.
     * Не помечаем @Primary — основным остаётся Redis CacheManager из CacheConfig.
     *
     * SimpleCacheManager хранит заранее сконфигурированные Cache-объекты.
     * CategoryService обращается к нему явно через cacheManager = "readThroughCacheManager".
     */
    @Bean
    public CacheManager readThroughCacheManager(HazelcastInstance hazelcastClient,
                                                 CategoryMapLoader mapLoader) {
        IMap<Object, Object> categoriesMap = hazelcastClient.getMap("categories");
        ReadThroughHazelcastCache cache = new ReadThroughHazelcastCache(
                "categories", categoriesMap, mapLoader, 60
        );

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(cache));
        return manager;
    }
}
