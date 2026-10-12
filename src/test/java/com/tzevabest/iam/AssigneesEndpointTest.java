package com.tzevabest.iam;

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
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Список исполнителей заявок: только активный персонал заявок, без контактов; склад и клиент — 403. */
class AssigneesEndpointTest extends IntegrationTest {

    private static final String PATH = "/api/v1/admin/users/assignees";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;

    @Test
    void lists_active_request_staff_only() throws Exception {
        long manager = user("MANAGER", "ACTIVE", "Dana", "Levi");
        long support = user("SUPPORT", "ACTIVE", "Avi", "Cohen");
        long admin = user("ADMIN", "ACTIVE", "", " ");
        long warehouse = user("WAREHOUSE", "ACTIVE", "Yossi", "K");
        long blocked = user("MANAGER", "BLOCKED", "Old", "Manager");
        long customer = user("CUSTOMER", "ACTIVE", "Client", "X");

        ResponseEntity<String> resp = get("MANAGER", manager);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode list = json.readTree(resp.getBody());
        List<Long> ids = new ArrayList<>();
        list.forEach(n -> ids.add(n.get("id").asLong()));

        assertThat(ids).contains(manager, support, admin).doesNotContain(warehouse, blocked, customer);
        JsonNode dana = find(list, manager);
        assertThat(dana.get("name").asText()).isEqualTo("Dana Levi");
        assertThat(dana.get("role").asText()).isEqualToIgnoringCase("manager");
        // контакты наружу не отдаются; пустое имя подменяется email
        assertThat(dana.has("email")).isFalse();
        assertThat(find(list, admin).get("name").asText()).contains("@x.test");

        assertThat(get("SUPPORT", support).getStatusCode().value()).isEqualTo(200);
        assertThat(get("WAREHOUSE", warehouse).getStatusCode().value()).isEqualTo(403);
        assertThat(get("CUSTOMER", customer).getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<String> get(String role, long userId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.generateAccess(userId, role, "t@x.test", "he"));
        return rest.exchange(PATH, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private long user(String role, String status, String first, String last) {
        return jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, first_name, last_name, role, status)
                values (?, 'x', ?, ?, ?, ?) returning id""",
                Long.class, role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@x.test",
                first, last, role, status);
    }

    private static JsonNode find(JsonNode list, long id) {
        for (JsonNode n : list) {
            if (n.get("id").asLong() == id) {
                return n;
            }
        }
        throw new AssertionError("no user " + id);
    }
}
