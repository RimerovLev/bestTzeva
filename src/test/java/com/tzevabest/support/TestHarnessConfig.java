package com.tzevabest.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Общая инфраструктура тестового харнеса. Безопасность обеспечивает боевой SecurityConfig (iam.web).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestHarnessConfig {

    @Bean
    TestDataFactory testDataFactory(JdbcTemplate jdbcTemplate) {
        return new TestDataFactory(jdbcTemplate);
    }
}
