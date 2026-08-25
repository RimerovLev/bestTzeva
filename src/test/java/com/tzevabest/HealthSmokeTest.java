package com.tzevabest;

import com.tzevabest.support.RestIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke: контекст поднимается и {@code /actuator/health} отвечает UP. */
class HealthSmokeTest extends RestIntegrationTest {

    @Test
    void context_loads_and_health_is_up() {
        ResponseEntity<String> response = getAs(TestRole.ANONYMOUS, "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
