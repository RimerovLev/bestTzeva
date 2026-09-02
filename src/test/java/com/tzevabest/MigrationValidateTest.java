package com.tzevabest;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-14.3: все миграции применяются на чистой базе и Hibernate validate проходит. Сам факт загрузки
 * контекста доказывает это (ddl-auto=validate); дополнительно проверяем, что нет упавших миграций.
 */
class MigrationValidateTest extends IntegrationTest {

    @Test
    void all_migrations_applied_successfully() {
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        assertThat(failed).isZero();
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(46); // V1..V46+
    }
}
