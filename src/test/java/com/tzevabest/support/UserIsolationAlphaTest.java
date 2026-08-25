package com.tzevabest.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Первый из пары тестов, доказывающих изоляцию: стартует с чистой таблицы, создаёт
 * ровно одного пользователя и проверяет, что использует тот же singleton-контейнер.
 * Парный класс — {@link UserIsolationBravoTest}.
 */
class UserIsolationAlphaTest extends IntegrationTest {

    @Test
    void starts_clean_and_creates_only_its_own_user() {
        assertThat(countUsers()).isZero();

        factory.createUser();

        assertThat(countUsers()).isEqualTo(1);
        SharedContainerProbe.assertSameContainer(POSTGRES);
    }

    private long countUsers() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
        return count != null ? count : 0L;
    }
}
