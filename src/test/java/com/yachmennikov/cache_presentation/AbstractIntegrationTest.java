package com.yachmennikov.cache_presentation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Базовый класс для интеграционных тестов.
 *
 * Redis поднимается в отдельном Testcontainers-контейнере на случайном порту
 * (не конфликтует с портом 6379 из docker-compose).
 *
 * Flyway замокан, чтобы предотвратить запуск SQL-миграций:
 * FlywayConfig объявляет бин через @Bean(initMethod = "migrate"),
 * и spring.flyway.enabled=false его не отключает — поэтому нужен @MockitoBean.
 *
 * JPA поднимается на H2 in-memory с ddl-auto=create-drop:
 * схема генерируется Hibernate из аннотаций сущностей, без SQL-файлов.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
public abstract class AbstractIntegrationTest {

    @MockitoBean
    Flyway flyway;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    /**
     * Полностью очищает Redis перед каждым тестом через FLUSHDB.
     *
     * Почему не CacheManager.getCache(...).clear():
     * RedisCacheWriter.clean() использует SCAN + DEL — два отдельных round-trip к Redis.
     * Между ними Lettuce может буферизировать команды в pipeline,
     * и ключ оказывается удалён уже после того как тест стартовал.
     * FLUSHDB — атомарная синхронная команда: Redis выполняет её целиком
     * до возврата ответа клиенту, гарантируя чистое состояние.
     */
    @BeforeEach
    void flushRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    // Тестовый Redis слушает на порту 6380 внутри контейнера —
    // отличается от боевого 6379 из docker-compose на уровне самого процесса Redis
    static final int REDIS_TEST_PORT = 6380;

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withCommand("redis-server --port " + REDIS_TEST_PORT)
                    .withExposedPorts(REDIS_TEST_PORT);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_TEST_PORT));
    }
}
