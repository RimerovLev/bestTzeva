package com.tzevabest.admin;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-21: audit_log и consents append-only на уровне БД — UPDATE и DELETE отклоняются триггером,
 * INSERT разрешён. Журнал, который нельзя править тем же соединением, что и данные.
 */
class AppendOnlyLogTest extends IntegrationTest {

    @Test
    void audit_log_rejects_update_and_delete() {
        jdbcTemplate.update("""
                insert into audit_log (action, entity_type, entity_id) values ('CREATE', 'order', 1)""");
        Long id = jdbcTemplate.queryForObject("select id from audit_log limit 1", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("update audit_log set action = 'X' where id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("delete from audit_log where id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void consents_reject_update_and_delete() {
        jdbcTemplate.update("""
                insert into consents (contact, consent_type, granted, policy_version) values ('u@x.test', 'DATA_PROCESSING', true, '2026-08-01')""");
        Long id = jdbcTemplate.queryForObject("select id from consents limit 1", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("update consents set granted = false where id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("delete from consents where id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void insert_is_still_allowed() {
        assertThatCode(() -> jdbcTemplate.update("""
                insert into audit_log (action, entity_type) values ('READ', 'user')"""))
                .doesNotThrowAnyException();
        assertThatCode(() -> jdbcTemplate.update("""
                insert into consents (contact, consent_type, granted, policy_version) values ('a@x.test', 'MARKETING', true, '2026-08-01')"""))
                .doesNotThrowAnyException();
    }
}
