package com.yachmennikov.cache_presentation.strategy.readthrough;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Загрузчик данных для Read-Through стратегии.
 *
 * В отличие от Caffeine (где CacheLoader — лямбда прямо в конфиге),
 * здесь логика загрузки вынесена в отдельный Spring-компонент.
 * Это позволяет внедрять зависимости через конструктор (DI работает корректно).
 *
 * Вызывается из ReadThroughHazelcastCache при cache miss —
 * то есть из слоя кэша, а не из сервисного слоя. В этом суть Read-Through.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryMapLoader {

    private final CategoryRepository repository;

    /**
     * Загружает категорию из БД по ключу.
     * Возвращает null если запись не найдена — Spring @Cacheable
     * вызовет тело метода сервиса как fallback, который бросит исключение.
     */
    public Category load(Object key) {
        log.info("[MAP LOADER] Cache miss — loading category {} from database", key);
        return repository.findById((Long) key).orElse(null);
    }
}
