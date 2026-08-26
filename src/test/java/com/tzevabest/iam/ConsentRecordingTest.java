package com.tzevabest.iam;

import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.service.AuthService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-39/R-24: при регистрации согласие на обработку данных пишется в consents с непустой версией
 * политики и IP. (Согласие при отправке заявки — путь T-11.1; должен вызывать тот же ConsentService.)
 */
class ConsentRecordingTest extends IntegrationTest {

    @Autowired
    private AuthService authService;

    @Test
    void registration_records_consent_with_policy_version() {
        String email = "consent-" + UUID.randomUUID().toString().substring(0, 8) + "@tzevabest.test";
        authService.register(new RegisterRequest(email, null, "Passw0rd", "A", "B", null), "ua", "9.9.9.9");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select consent_type, policy_version, ip, granted from consents where contact = ?", email);

        assertThat(row.get("consent_type")).isEqualTo("DATA_PROCESSING");
        assertThat((String) row.get("policy_version")).isNotBlank();
        assertThat(row.get("ip")).isEqualTo("9.9.9.9");
        assertThat(row.get("granted")).isEqualTo(true);
    }
}
