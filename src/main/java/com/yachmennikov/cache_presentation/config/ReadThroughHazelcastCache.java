package com.yachmennikov.cache_presentation.config;

import com.hazelcast.map.IMap;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Кастомная реализация Spring Cache поверх Hazelcast IMap.
 *
 * Ключевая особенность — Read-Through в методе get():
 * при cache miss кэш сам загружает данные из БД через CategoryMapLoader,
 * не возвращая управление в сервисный слой.
 *
 * Это то же поведение, что даёт Caffeine LoadingCache,
 * но реализованное вручную для Hazelcast client-server режима.
 * (В Hazelcast MapLoader работает только на стороне сервера,
 *  поэтому логику загрузки мы держим на стороне клиента.)
 */
@RequiredArgsConstructor
public class ReadThroughHazelcastCache implements Cache {

    private final String name;
    private final IMap<Object, Object> nativeMap;
    private final CategoryMapLoader loader;
    private final long ttlSeconds;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return nativeMap;
    }

    /**
     * Read-Through: при cache miss сразу загружаем из БД и кладём в кэш.
     * Тело метода сервиса (@Cacheable) при этом не вызывается.
     */
    @Override
    public ValueWrapper get(Object key) {
        Object value = nativeMap.get(key);
        if (value == null) {
            value = loader.load(key);
            if (value != null) {
                nativeMap.put(key, value, ttlSeconds, TimeUnit.SECONDS);
            }
        }
        return value != null ? new SimpleValueWrapper(value) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        return wrapper != null ? (T) wrapper.get() : null;
    }

    /**
     * Используется Spring как запасной путь если get() вернул null.
     * В нашем случае get() уже реализует Read-Through, поэтому сюда
     * попадаем только если сущность не найдена ни в кэше, ни в БД.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (value != null) {
            nativeMap.put(key, value, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public void evict(Object key) {
        nativeMap.remove(key);
    }

    @Override
    public void clear() {
        nativeMap.clear();
    }
}
