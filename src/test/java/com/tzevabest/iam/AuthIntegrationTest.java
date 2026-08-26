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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Интеграционные тесты аутентификации: register, login (успех/провал), refresh, logout, /me. */
class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JwtService jwt;

    @Test
    void refresh_token_is_rejected_as_bearer() throws Exception {
        ResponseEntity<String> reg = register(body("email", "u-" + token() + "@tzevabest.test",
                "password", "Passw0rd", "first_name", "A", "last_name", "B"));
        String refresh = field(reg, "refresh_token");

        // refresh-токен в Authorization: Bearer не должен приниматься как access
        ResponseEntity<String> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(refresh), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void access_token_without_role_is_rejected() {
        // токен с правильным type=access, но без claim role — не должен давать ROLE_null
        String noRole = jwt.generateAccess(1L, null, "x-" + token() + "@tzevabest.test", "he");
        ResponseEntity<String> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(noRole), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_then_access_me() throws Exception {
        String email = "u-" + token() + "@tzevabest.test";
        ResponseEntity<String> reg = register(body("email", email, "password", "Passw0rd", "first_name", "A", "last_name", "B"));
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String access = field(reg, "access_token");

        ResponseEntity<String> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(access), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(field(me, "email")).isEqualTo(email);
        assertThat(field(me, "role")).isEqualTo("customer");
    }

    @Test
    void login_by_email_and_by_phone() throws Exception {
        String email = "u-" + token() + "@tzevabest.test";
        register(body("email", email, "password", "Passw0rd", "first_name", "A", "last_name", "B"));
        ResponseEntity<String> byEmail = post("/api/v1/auth/login", body("login", email, "password", "Passw0rd"));
        assertThat(byEmail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(field(byEmail, "access_token")).isNotBlank();

        register(body("phone", "0501234567", "password", "Passw0rd", "first_name", "P", "last_name", "H"));
        ResponseEntity<String> byPhone = post("/api/v1/auth/login", body("login", "050-123-4567", "password", "Passw0rd"));
        assertThat(byPhone.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrong_password_and_unknown_user_look_identical() throws Exception {
        String email = "u-" + token() + "@tzevabest.test";
        register(body("email", email, "password", "Passw0rd", "first_name", "A", "last_name", "B"));

        ResponseEntity<String> wrongPass = post("/api/v1/auth/login", body("login", email, "password", "WrongPass9"));
        ResponseEntity<String> unknown = post("/api/v1/auth/login", body("login", "nobody-" + token() + "@x.test", "password", "WrongPass9"));

        assertThat(wrongPass.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(field(wrongPass, "code")).isEqualTo("common.auth.invalid_credentials");
        assertThat(field(unknown, "code")).isEqualTo("common.auth.invalid_credentials");
    }

    @Test
    void refresh_rotates_and_invalidates_old_token() throws Exception {
        ResponseEntity<String> reg = register(body("email", "u-" + token() + "@tzevabest.test",
                "password", "Passw0rd", "first_name", "A", "last_name", "B"));
        String oldRefresh = field(reg, "refresh_token");

        ResponseEntity<String> refreshed = post("/api/v1/auth/refresh", body("refresh_token", oldRefresh));
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(field(refreshed, "refresh_token")).isNotEqualTo(oldRefresh);

        // повторное использование старого refresh запрещено
        ResponseEntity<String> reused = post("/api/v1/auth/refresh", body("refresh_token", oldRefresh));
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(field(reused, "code")).isEqualTo("common.auth.refresh_invalid");
    }

    @Test
    void logout_revokes_refresh_token() throws Exception {
        ResponseEntity<String> reg = register(body("email", "u-" + token() + "@tzevabest.test",
                "password", "Passw0rd", "first_name", "A", "last_name", "B"));
        String access = field(reg, "access_token");
        String refresh = field(reg, "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(access);
        ResponseEntity<String> logout = rest.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(body("refresh_token", refresh), headers), String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterLogout = post("/api/v1/auth/refresh", body("refresh_token", refresh));
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void me_requires_authentication() {
        ResponseEntity<String> me = rest.getForEntity("/api/v1/auth/me", String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void weak_password_is_rejected() throws Exception {
        ResponseEntity<String> reg = register(body("email", "u-" + token() + "@tzevabest.test",
                "password", "abc", "first_name", "A", "last_name", "B"));
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(field(reg, "code")).isEqualTo("common.validation.failed");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private ResponseEntity<String> register(String jsonBody) {
        return post("/api/v1/auth/register", jsonBody);
    }

    private ResponseEntity<String> post(String path, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(jsonBody, headers), String.class);
    }

    private HttpEntity<Void> bearer(String access) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(access);
        return new HttpEntity<>(headers);
    }

    private String body(String... kv) {
        try {
            var node = json.createObjectNode();
            for (int i = 0; i < kv.length; i += 2) {
                node.put(kv[i], kv[i + 1]);
            }
            return json.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String field(ResponseEntity<String> response, String name) throws Exception {
        JsonNode node = json.readTree(response.getBody());
        return node.hasNonNull(name) ? node.get(name).asText() : null;
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
