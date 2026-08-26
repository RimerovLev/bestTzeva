package com.tzevabest.service;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-19: без согласия заявка на услугу не принимается — consent_given_at обязателен на уровне БД
 * (не только в контроллере). Соответствие израильскому закону о приватности для формы с адресом и фото.
 */
class ServiceRequestConsentRequiredTest extends IntegrationTest {

    @Test
    void inserting_request_without_consent_is_rejected() {
        assertThatThrownBy(() -> insert(null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void request_with_consent_is_accepted() {
        assertThatCode(() -> insert(Instant.now())).doesNotThrowAnyException();
    }

    private void insert(Instant consentGivenAt) {
        jdbcTemplate.update("""
                        insert into service_requests
                            (request_number, customer_name, customer_phone, property_type,
                             source, consent_given_at)
                        values (?, 'Client', '+972500000000', 'APARTMENT', 'SITE_FORM', ?)""",
                "SR-" + UUID.randomUUID().toString().substring(0, 8),
                consentGivenAt == null ? null : java.sql.Timestamp.from(consentGivenAt));
    }
}
