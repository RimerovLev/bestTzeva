package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-4.6: палитра цветов — админский CRUD и публичный список с локализацией имени, фильтрами и поиском.
 */
class ColorPaletteTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JwtService jwt;
    @Autowired
    private ObjectMapper json;

    @Test
    void admin_creates_and_public_lists_localized_and_filtered() throws Exception {
        create("{\"code\":\"RAL5010\",\"system\":\"RAL\",\"hex\":\"#123456\","
                + "\"name_translations\":{\"he\":\"כחול\",\"en\":\"Blue\"},\"popular\":true}");
        create("{\"code\":\"NCS-S1000\",\"system\":\"NCS\",\"hex\":\"#abcdef\","
                + "\"name_translations\":{\"he\":\"לבן\"},\"popular\":false}");

        JsonNode all = get("/api/v1/colors");
        assertThat(all).hasSize(2);

        // локализация: HE по умолчанию, EN по параметру
        JsonNode he = get("/api/v1/colors?system=RAL");
        assertThat(he).hasSize(1);
        assertThat(he.get(0).get("name").asText()).isEqualTo("כחול");
        assertThat(get("/api/v1/colors?system=RAL&locale=en").get(0).get("name").asText()).isEqualTo("Blue");

        // фильтр по популярности
        assertThat(get("/api/v1/colors?popular=true")).hasSize(1);
        // поиск по имени (иврит)
        assertThat(get("/api/v1/colors?q=כחול")).hasSize(1);
    }

    @Test
    void duplicate_code_is_rejected() throws Exception {
        create("{\"code\":\"RAL9999\",\"system\":\"RAL\",\"name_translations\":{\"he\":\"a\"}}");
        ResponseEntity<String> resp = admin(HttpMethod.POST, "/api/v1/admin/colors",
                "{\"code\":\"RAL9999\",\"system\":\"RAL\",\"name_translations\":{\"he\":\"b\"}}");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(resp.getBody()).get("code").asText()).isEqualTo("catalog.color.code_taken");
    }

    @Test
    void delete_removes_swatch() throws Exception {
        ResponseEntity<String> created = admin(HttpMethod.POST, "/api/v1/admin/colors",
                "{\"code\":\"RAL0001\",\"system\":\"RAL\",\"name_translations\":{\"he\":\"x\"}}");
        long id = json.readTree(created.getBody()).get("id").asLong();
        assertThat(admin(HttpMethod.DELETE, "/api/v1/admin/colors/" + id, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/colors")).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void create(String body) {
        assertThat(admin(HttpMethod.POST, "/api/v1/admin/colors", body).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> admin(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@tzevabest.test", "he"));
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode get(String path) throws Exception {
        return json.readTree(rest.getForEntity(path, String.class).getBody());
    }
}
