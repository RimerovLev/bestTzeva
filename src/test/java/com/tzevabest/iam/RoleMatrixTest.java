package com.tzevabest.iam;

import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Матрица «роль × эндпоинт» из docs/roles.md: сверяет фактический статус (200/403) с таблицей прав.
 * Плюс проверка владения ресурсом (адрес): владелец/чужой/привилегированная роль.
 */
class RoleMatrixTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JwtService jwt;

    /** Один пользователь на роль (id синтетический — проверяются только права из claim role). */
    private static long userId(String role) {
        return switch (role) {
            case "CUSTOMER" -> 9_000_001L;
            case "SUPPORT" -> 9_000_002L;
            case "WAREHOUSE" -> 9_000_003L;
            case "MANAGER" -> 9_000_004L;
            default -> 9_000_005L; // ADMIN
        };
    }

    /** Таблица прав из docs/roles.md: метод, путь, и ожидаемый статус по ролям (C/SU/WH/M/A). */
    static Stream<Arguments> matrix() {
        record Endpoint(HttpMethod method, String path, int customer, int support, int warehouse, int manager, int admin) {
        }
        List<Endpoint> endpoints = List.of(
                new Endpoint(HttpMethod.GET, "/api/v1/admin/orders", 403, 200, 200, 200, 200),
                new Endpoint(HttpMethod.PUT, "/api/v1/admin/inventory", 403, 403, 200, 200, 200),
                new Endpoint(HttpMethod.PUT, "/api/v1/admin/service-requests/1/status", 403, 200, 403, 200, 200),
                new Endpoint(HttpMethod.PUT, "/api/v1/admin/catalog", 403, 403, 403, 200, 200),
                new Endpoint(HttpMethod.GET, "/api/v1/admin/reports/finance", 403, 403, 403, 200, 200));

        String[] roles = {"CUSTOMER", "SUPPORT", "WAREHOUSE", "MANAGER", "ADMIN"};
        return endpoints.stream().flatMap(e -> Stream.of(
                Arguments.of("CUSTOMER", e.method(), e.path(), e.customer()),
                Arguments.of("SUPPORT", e.method(), e.path(), e.support()),
                Arguments.of("WAREHOUSE", e.method(), e.path(), e.warehouse()),
                Arguments.of("MANAGER", e.method(), e.path(), e.manager()),
                Arguments.of("ADMIN", e.method(), e.path(), e.admin())));
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @MethodSource("matrix")
    void role_endpoint_matrix(String role, HttpMethod method, String path, int expectedStatus) {
        int actual = rest.exchange(path, method, new HttpEntity<>(headers(role)), String.class)
                .getStatusCode().value();
        assertThat(actual).isEqualTo(expectedStatus);
    }

    @Test
    void change_role_endpoint_is_admin_only() {
        TestDataFactory.TestUser target = factory.createUser();
        String path = "/api/v1/admin/users/" + target.id() + "/role";
        String body = "{\"role\":\"support\"}";

        // не-ADMIN получают 403 (CUSTOMER — на цепочке, служебные — на @PreAuthorize сервиса)
        for (String role : List.of("CUSTOMER", "SUPPORT", "WAREHOUSE", "MANAGER")) {
            assertThat(post(path, body, role)).isEqualTo(403);
        }
        assertThat(post(path, body, "ADMIN")).isEqualTo(200);

        // роль действительно сменилась + запись в audit_log
        String newRole = jdbcTemplate.queryForObject("select role from users where id = ?", String.class, target.id());
        assertThat(newRole).isEqualTo("SUPPORT");
        Integer audit = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action='user.role.changed' and entity_id=?",
                Integer.class, target.id());
        assertThat(audit).isEqualTo(1);
    }

    @Test
    void address_ownership_owner_other_and_privileged() {
        TestDataFactory.TestUser owner = factory.createUser();
        TestDataFactory.TestUser other = factory.createUser();
        Long addressId = jdbcTemplate.queryForObject("""
                        insert into user_addresses (user_id, city, street, house, is_default)
                        values (?, 'Tel Aviv', 'Herzl', '1', true) returning id""",
                Long.class, owner.id());
        String path = "/api/v1/me/addresses/" + addressId;

        assertThat(get(path, tokenFor("CUSTOMER", owner.id()))).isEqualTo(200);   // владелец
        assertThat(get(path, tokenFor("CUSTOMER", other.id()))).isEqualTo(403);   // чужой
        assertThat(get(path, tokenFor("MANAGER", other.id()))).isEqualTo(200);    // привилегия
        assertThat(get(path, tokenFor("ADMIN", other.id()))).isEqualTo(200);      // привилегия
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private int post(String path, String body, String role) {
        HttpHeaders h = headers(role);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class).getStatusCode().value();
    }

    private int get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class).getStatusCode().value();
    }

    private HttpHeaders headers(String role) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tokenFor(role, userId(role)));
        return h;
    }

    private String tokenFor(String role, long userId) {
        return jwt.generateAccess(userId, role, "user@tzevabest.test", "he");
    }
}
