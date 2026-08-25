package com.tzevabest.common.web;

import com.tzevabest.support.RestIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет генерацию обоих документов OpenAPI (public/admin), доступность swagger-ui
 * и сквозной заголовок X-Request-Id.
 */
class OpenApiDocsTest extends RestIntegrationTest {

    @Test
    void public_document_generates() {
        ResponseEntity<String> resp = getAs(TestRole.ANONYMOUS, "/api-docs/public", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("\"openapi\"");
    }

    @Test
    void admin_document_generates() {
        ResponseEntity<String> resp = getAs(TestRole.ANONYMOUS, "/api-docs/admin", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("\"openapi\"");
    }

    @Test
    void swagger_config_lists_both_groups() {
        ResponseEntity<String> resp = getAs(TestRole.ANONYMOUS, "/api-docs/swagger-config", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("public").contains("admin");
    }

    @Test
    void swagger_ui_is_available() {
        ResponseEntity<String> resp = getAs(TestRole.ANONYMOUS, "/swagger-ui/index.html", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void request_id_from_header_is_echoed_back() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(RequestIdFilter.HEADER, "test-req-123");

        ResponseEntity<String> resp = rest.exchange(
                "/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(resp.getHeaders().getFirst(RequestIdFilter.HEADER)).isEqualTo("test-req-123");
    }

    @Test
    void request_id_is_generated_when_absent() {
        ResponseEntity<String> resp = getAs(TestRole.ANONYMOUS, "/actuator/health", String.class);

        String id = resp.getHeaders().getFirst(RequestIdFilter.HEADER);
        assertThat(id).isNotBlank();
    }
}
