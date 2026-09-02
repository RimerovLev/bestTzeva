package com.tzevabest.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.stream.Collectors;

/**
 * База для интеграционных тестов бэкенда.
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} в профиле {@code test};</li>
 *   <li>PostgreSQL 16 и Redis 7 поднимаются через Testcontainers и подключаются
 *       к контексту через {@link ServiceConnection};</li>
 *   <li><b>singleton-контейнеры</b>: статические поля стартуют один раз на JVM и
 *       переиспользуются всеми классами тестов (контейнер не пересоздаётся на класс);</li>
 *   <li>откат состояния между тестами в {@link #resetState()} — TRUNCATE всех таблиц
 *       (кроме {@code flyway_schema_history}) и {@code flushDb} в Redis;</li>
 *   <li>без {@code @DirtiesContext} — контекст кэшируется и переиспользуется.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({IntegrationTest.ContainersConfig.class, TestHarnessConfig.class})
public abstract class IntegrationTest {

    // Singleton-контейнеры: стартуют единожды при загрузке класса, живут до конца JVM
    // (останавливает Ryuk). Одни и те же инстансы наследуются всеми подклассами.
    protected static final PostgreSQLContainer<?> POSTGRES;
    protected static final GenericContainer<?> REDIS;
    protected static final MinIOContainer MINIO;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
        MINIO = new MinIOContainer(DockerImageName.parse("minio/minio:latest"));
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
    }

    /** Направляет S3-хранилище медиа (T-4.7) на MinIO-контейнер. */
    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.s3.endpoint", MINIO::getS3URL);
        registry.add("app.storage.s3.access-key", MINIO::getUserName);
        registry.add("app.storage.s3.secret-key", MINIO::getPassword);
        registry.add("app.storage.s3.bucket", () -> "test-media");
        registry.add("app.storage.s3.path-style-access", () -> true);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    @Autowired
    protected TestDataFactory factory;

    @Autowired(required = false)
    private org.springframework.cache.CacheManager cacheManager;

    /**
     * Полный откат состояния после каждого теста, чтобы тесты не видели данные друг друга.
     * TRUNCATE ... RESTART IDENTITY CASCADE сбрасывает и содержимое, и счётчики
     * последовательностей; таблица истории миграций Flyway не трогается.
     */
    @AfterEach
    void resetState() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                        + "AND tablename <> 'flyway_schema_history'",
                String.class);

        if (!tables.isEmpty()) {
            String joined = tables.stream()
                    .map(t -> '"' + t + '"')
                    .collect(Collectors.joining(", "));
            jdbcTemplate.execute("TRUNCATE TABLE " + joined + " RESTART IDENTITY CASCADE");
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }

        // Приложенческие кэши (напр. дашборд) не связаны с БД — чистим, чтобы тесты не видели данные друг друга.
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(name -> {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        }
    }

    /** Регистрирует singleton-контейнеры как источники подключения Spring Boot. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ContainersConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return POSTGRES;
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return REDIS;
        }
    }
}
