package com.tzevabest.iam;

import com.tzevabest.iam.service.JwtService;
import com.tzevabest.iam.service.RefreshTokenStore;
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

    @Autowired
    private RefreshTokenStore refreshStore;

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

    /**
     * Матрица прав из docs/roles.md на РЕАЛЬНЫХ эндпоинтах (заглушки AdminController удалены в B-03).
     * Каждая строка проверяет свою границу роли на GET-эндпоинте (тело не валидируется раньше authz):
     * склад — WAREHOUSE+, заявки на покраску — SUPPORT+ (но не WAREHOUSE), каталог — MANAGER+.
     * Значение по роли: 403 — доступ запрещён; 200 — «не запрещён» (проверяется как «не 403»).
     */
    static Stream<Arguments> matrix() {
        record Endpoint(HttpMethod method, String path, int customer, int support, int warehouse, int manager, int admin) {
        }
        List<Endpoint> endpoints = List.of(
                new Endpoint(HttpMethod.GET, "/api/v1/admin/inventory/low-stock", 403, 403, 200, 200, 200),
                new Endpoint(HttpMethod.GET, "/api/v1/admin/service-requests/metrics", 403, 200, 403, 200, 200),
                new Endpoint(HttpMethod.GET, "/api/v1/admin/products", 403, 403, 403, 200, 200));

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
        // 403 сверяем точно (отказ авторизации); «разрешено» — как «не 403» (реальный эндпоинт может
        // вернуть 200/400/404 по бизнес-причинам, но не отказ доступа).
        if (expectedStatus == 403) {
            assertThat(actual).isEqualTo(403);
        } else {
            assertThat(actual).isNotEqualTo(403);
        }
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
    void change_role_revokes_target_sessions() {
        // Аудит п.12: понижение прав должно действовать сразу — refresh-сессии отзываются.
        TestDataFactory.TestUser target = factory.createUser(s -> s.role = "SUPPORT");
        refreshStore.issue(target.id(), "t1", "r1", "ua", "1.1.1.1", java.time.Duration.ofMinutes(30));

        assertThat(post("/api/v1/admin/users/" + target.id() + "/role", "{\"role\":\"warehouse\"}", "ADMIN"))
                .isEqualTo(200);

        // сессия больше не валидна
        assertThat(refreshStore.validateAndRotate(target.id(), "t1", "r1", 1_800_000L)).isFalse();
    }

    @Test
    void cannot_demote_last_active_admin() {
        // Единственный активный ADMIN в БД — попытка понизить даёт 409, роль не меняется (аудит п.12).
        TestDataFactory.TestUser admin = factory.createUser(s -> s.role = "ADMIN");
        assertThat(post("/api/v1/admin/users/" + admin.id() + "/role", "{\"role\":\"manager\"}", "ADMIN"))
                .isEqualTo(409);
        String role = jdbcTemplate.queryForObject("select role from users where id = ?", String.class, admin.id());
        assertThat(role).isEqualTo("ADMIN");
    }

    @Test
    void public_registration_is_closed_by_default() {
        // Флаг app.auth.public-registration-enabled по умолчанию false → 404 (не 403: не подтверждаем
        // существование закрытого эндпоинта). Включённый путь покрыт AuthIntegrationTest (флаг on).
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"nobody-" + System.nanoTime() + "@x.test\",\"password\":\"Passw0rd\","
                + "\"first_name\":\"A\",\"last_name\":\"B\"}";
        var resp = rest.exchange("/api/v1/auth/register", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
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
