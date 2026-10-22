package com.tzevabest.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.common.web.error.BadRequestException;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.iam.service.OneTimeTokenStore;
import com.tzevabest.iam.service.PasswordService;
import com.tzevabest.iam.service.RefreshTokenStore;
import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Аудит п.3: заведение сотрудника (invite → set-password → ACTIVE) и блокировка/разблокировка. */
class AdminUserManagementTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JwtService jwt;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private RefreshTokenStore refreshStore;
    @Autowired
    private OneTimeTokenStore tokens;
    @Autowired
    private PasswordService passwordService;

    private String admin() {
        return jwt.generateAccess(9_000_005L, "ADMIN", "a@x.test", "he");
    }

    @Test
    void create_employee_is_pending_then_invite_reset_activates() throws Exception {
        String email = "emp-" + System.nanoTime() + "@x.test";
        String body = "{\"email\":\"" + email + "\",\"first_name\":\"E\",\"last_name\":\"M\",\"role\":\"support\"}";
        ResponseEntity<String> resp = post("/api/v1/admin/users", body, admin());
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        long id = json.readTree(resp.getBody()).get("id").asLong();

        assertThat(statusOf(id)).isEqualTo("PENDING");

        // приглашение завершается заданием пароля по одноразовому токену (тот же namespace, что reset)
        String token = tokens.issue("pwd-reset", id, Duration.ofHours(1));
        passwordService.reset(token, "Passw0rd!");
        assertThat(statusOf(id)).isEqualTo("ACTIVE");
    }

    @Test
    void create_employee_rejects_duplicate_email_and_customer_role() {
        String email = "dup-" + System.nanoTime() + "@x.test";
        String body = "{\"email\":\"" + email + "\",\"first_name\":\"E\",\"last_name\":\"M\",\"role\":\"support\"}";
        assertThat(post("/api/v1/admin/users", body, admin()).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/api/v1/admin/users", body, admin()).getStatusCode().value()).isEqualTo(409); // дубль
        String asCustomer = "{\"email\":\"c-" + System.nanoTime()
                + "@x.test\",\"first_name\":\"E\",\"last_name\":\"M\",\"role\":\"customer\"}";
        assertThat(post("/api/v1/admin/users", asCustomer, admin()).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void non_admin_cannot_create_employee() {
        String body = "{\"email\":\"x-" + System.nanoTime()
                + "@x.test\",\"first_name\":\"E\",\"last_name\":\"M\",\"role\":\"support\"}";
        assertThat(post("/api/v1/admin/users", body, role("MANAGER")).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void block_closes_login_and_revokes_sessions_unblock_restores() {
        TestDataFactory.TestUser u = factory.createUser(s -> s.role = "SUPPORT");
        refreshStore.issue(u.id(), "t1", "r1", "ua", "1.1.1.1", Duration.ofMinutes(30));

        assertThat(post("/api/v1/admin/users/" + u.id() + "/block", null, admin()).getStatusCode().value())
                .isEqualTo(200);
        assertThat(statusOf(u.id())).isEqualTo("BLOCKED");
        assertThat(refreshStore.validateAndRotate(u.id(), "t1", "r1", 1_800_000L)).isFalse(); // сессия отозвана

        assertThat(post("/api/v1/admin/users/" + u.id() + "/unblock", null, admin()).getStatusCode().value())
                .isEqualTo(200);
        assertThat(statusOf(u.id())).isEqualTo("ACTIVE");
    }

    @Test
    void last_admin_check_is_race_safe() throws Exception {
        // Аудит п.12 (перепроверка): два одновременных понижения двух последних ADMIN не должны
        // оставить 0 администраторов. Advisory-лок сериализует проверку → ровно один успех, один 409.
        TestDataFactory.TestUser a1 = factory.createUser(s -> s.role = "ADMIN");
        TestDataFactory.TestUser a2 = factory.createUser(s -> s.role = "ADMIN");

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.List<java.util.concurrent.Callable<Integer>> tasks = java.util.List.of(
                () -> post("/api/v1/admin/users/" + a1.id() + "/role", "{\"role\":\"manager\"}", admin())
                        .getStatusCode().value(),
                () -> post("/api/v1/admin/users/" + a2.id() + "/role", "{\"role\":\"manager\"}", admin())
                        .getStatusCode().value());
        var results = pool.invokeAll(tasks);
        int s1 = results.get(0).get();
        int s2 = results.get(1).get();
        pool.shutdown();

        assertThat(java.util.List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);
        Integer activeAdmins = jdbcTemplate.queryForObject(
                "select count(*) from users where role = 'ADMIN' and status = 'ACTIVE'", Integer.class);
        assertThat(activeAdmins).isEqualTo(1); // ровно один остался — не 0
    }

    @Test
    void cannot_block_last_active_admin() {
        TestDataFactory.TestUser a = factory.createUser(s -> s.role = "ADMIN");
        assertThat(post("/api/v1/admin/users/" + a.id() + "/block", null, admin()).getStatusCode().value())
                .isEqualTo(409);
        assertThat(statusOf(a.id())).isEqualTo("ACTIVE");
    }

    @Test
    void blocked_user_cannot_reset_password() {
        TestDataFactory.TestUser u = factory.createUser(s -> {
            s.role = "SUPPORT";
            s.status = "BLOCKED";
        });
        String token = tokens.issue("pwd-reset", u.id(), Duration.ofHours(1));
        assertThatThrownBy(() -> passwordService.reset(token, "Passw0rd!"))
                .isInstanceOf(BadRequestException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ResponseEntity<String> post(String path, String body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        if (body != null) {
            h.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private String role(String r) {
        return jwt.generateAccess(9_000_004L, r, r.toLowerCase() + "@x.test", "he");
    }

    private String statusOf(long id) {
        return jdbcTemplate.queryForObject("select status from users where id = ?", String.class, id);
    }
}
