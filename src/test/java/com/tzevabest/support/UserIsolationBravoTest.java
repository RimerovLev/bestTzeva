package com.tzevabest.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Второй из пары тестов изоляции. В другом классе — но начинает с той же чистой
 * таблицы и не видит пользователя, созданного в {@link UserIsolationAlphaTest},
 * при этом работает с тем же singleton-контейнером.
 */
class UserIsolationBravoTest extends IntegrationTest {

    @Test
    void starts_clean_and_creates_only_its_own_user() {
        assertThat(countUsers())
                .as("данные из другого класса тестов не должны быть видны")
                .isZero();

        factory.createUser();

        assertThat(countUsers()).isEqualTo(1);
        SharedContainerProbe.assertSameContainer(POSTGRES);
    }

    private long countUsers() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
        return count != null ? count : 0L;
    }
}
