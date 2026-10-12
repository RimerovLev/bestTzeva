package com.tzevabest.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-01: refresh-токен в httpOnly-куке. Тесты пути по куке (без тела):
 * вход ставит куку, обновление токена читает её без тела, выход гасит куку, чужой Origin отклоняется.
 * Запасной body-путь здесь ВЫКЛЮЧЕН ({@code refresh-token-in-body=false}) — проверяем именно куку.
 */
@TestPropertySource(properties = {
        "app.auth.public-registration-enabled=true",
        "app.auth.refresh-token-in-body=false",
})
class AuthCookieIntegrationTest extends IntegrationTest {

    private static final String COOKIE_NAME = "refresh_token";
    /** Origin из allow-list (app.cors.allowed-origins, дефолт для профиля test). */
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper json;

    @Test
    void login_sets_httponly_cookie_and_omits_refresh_from_body() throws Exception {
        ResponseEntity<String> reg = register();

        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // access в теле есть, refresh — нет: он ушёл в куку
        assertThat(field(reg, "access_token")).isNotBlank();
        assertThat(field(reg, "refresh_token")).isNull();

        String setCookie = firstSetCookie(reg);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(COOKIE_NAME + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/api/v1/auth");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(cookieValue(setCookie)).isNotBlank();
    }

    @Test
    void refresh_reads_cookie_without_body_and_rotates() throws Exception {
        ResponseEntity<String> reg = register();
        String refresh = cookieValue(firstSetCookie(reg));

        // ни тела, ни Authorization — только Cookie
        ResponseEntity<String> refreshed = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(null, cookie(refresh)), String.class);

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(field(refreshed, "access_token")).isNotBlank();
        // ротация: пришла новая кука с другим значением
        String rotated = cookieValue(firstSetCookie(refreshed));
        assertThat(rotated).isNotBlank().isNotEqualTo(refresh);
    }

    @Test
    void logout_clears_the_cookie() throws Exception {
        ResponseEntity<String> reg = register();
        String access = field(reg, "access_token");
        String refresh = cookieValue(firstSetCookie(reg));

        HttpHeaders headers = cookie(refresh);
        headers.setBearerAuth(access);
        ResponseEntity<String> logout = rest.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(null, headers), String.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = firstSetCookie(logout);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(COOKIE_NAME + "=");
        // гашение: пустое значение и Max-Age=0
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(cookieValue(setCookie)).isEmpty();

        // сессия действительно отозвана: повтор по той же куке — 401
        ResponseEntity<String> afterLogout = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(null, cookie(refresh)), String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_from_foreign_origin_is_rejected() {
        ResponseEntity<String> reg = register();
        String refresh = cookieValue(firstSetCookie(reg));

        HttpHeaders headers = cookie(refresh);
        headers.set(HttpHeaders.ORIGIN, "https://evil.example");
        ResponseEntity<String> refreshed = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(null, headers), String.class);

        // Чужой Origin отклоняется (403). На кросс-доменном запросе первым срабатывает CORS-фильтр
        // (allowCredentials + конкретный allow-list), поэтому тело — не problem+json. Собственный
        // контракт AuthOriginGuard (403 + code) покрыт юнит-тестом AuthOriginGuardTest: он защищает
        // от CSRF в сценарии, когда админка и API на одном домене и CORS такой запрос не ловит.
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refresh_from_allowed_origin_passes() throws Exception {
        ResponseEntity<String> reg = register();
        String refresh = cookieValue(firstSetCookie(reg));

        HttpHeaders headers = cookie(refresh);
        headers.set(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
        ResponseEntity<String> refreshed = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(null, headers), String.class);

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private ResponseEntity<String> register() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String jsonBody = body(
                "email", "u-" + token() + "@tzevabest.test",
                "password", "Passw0rd", "first_name", "A", "last_name", "B");
        return rest.exchange("/api/v1/auth/register", HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), String.class);
    }

    private static HttpHeaders cookie(String refresh) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, COOKIE_NAME + "=" + refresh);
        return headers;
    }

    private static String firstSetCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        return cookies.stream().filter(c -> c.startsWith(COOKIE_NAME + "=")).findFirst().orElse(null);
    }

    /** Значение куки из строки Set-Cookie: {@code name=value; Attr...} → value. */
    private static String cookieValue(String setCookie) {
        int eq = setCookie.indexOf('=');
        int semi = setCookie.indexOf(';');
        return setCookie.substring(eq + 1, semi < 0 ? setCookie.length() : semi);
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
