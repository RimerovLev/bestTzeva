package com.tzevabest.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Общая инфраструктура тестового харнеса.
 * <p>
 * Безопасность в интеграционных тестах обеспечивает боевой
 * {@code com.tzevabest.common.web.SecurityConfig} (открывает доки/health, остальное — до
 * появления IAM — permitAll), поэтому отдельная тестовая цепочка больше не нужна.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestHarnessConfig {

    @Bean
    TestDataFactory testDataFactory(JdbcTemplate jdbcTemplate) {
        return new TestDataFactory(jdbcTemplate);
    }
}
