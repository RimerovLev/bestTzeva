package com.tzevabest.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.notification.service.NotificationSender;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import org.springframework.test.context.TestPropertySource;

/** Подтверждение email, OTP телефона и восстановление/смена пароля (HTTP-флоу). */
@TestPropertySource(properties = "app.auth.public-registration-enabled=true")
class AuthRecoveryIntegrationTest extends IntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_\\-]+)");
    private static final Pattern CODE = Pattern.compile("(\\d{6})");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private NotificationSender notifications;

    @Test
    void email_verification_flow_with_reuse_rejected() throws Exception {
        String email = uniqueEmail();
        String access = accessFrom(register(email, null));

        assertThat(post("/api/v1/auth/email/send-verification", null, access).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        String token = extract(TOKEN, capturedEmailBody());
        // Подтверждение — POST (GET состояние не меняет, аудит п.14).
        assertThat(post("/api/v1/auth/email/verify", body("token", token), access).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        // повторное использование ссылки
        assertThat(post("/api/v1/auth/email/verify", body("token", token), access).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void otp_attempts_exceeded_blocks() throws Exception {
        String access = accessFrom(register(null, "0501112233"));
        assertThat(post("/api/v1/auth/phone/send-otp", null, access).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        String code = extract(CODE, capturedSms());
        for (int i = 0; i < 5; i++) {
            assertThat(post("/api/v1/auth/phone/verify-otp", body("code", "000000"), access).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(post("/api/v1/auth/phone/verify-otp", body("code", code), access).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void otp_resend_is_rate_limited() throws Exception {
        String access = accessFrom(register(null, "0502223344"));
        assertThat(post("/api/v1/auth/phone/send-otp", null, access).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(post("/api/v1/auth/phone/send-otp", null, access).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void password_reset_revokes_all_sessions() throws Exception {
        String email = uniqueEmail();
        ResponseEntity<String> reg = register(email, null);
        String oldRefresh = field(reg, "refresh_token");

        assertThat(post("/api/v1/auth/password/forgot", body("login", email), null).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        String token = extract(TOKEN, capturedEmailBody());

        assertThat(post("/api/v1/auth/password/reset",
                json.createObjectNode().put("token", token).put("new_password", "NewPass99").toString(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // старый refresh отозван
        assertThat(post("/api/v1/auth/refresh", body("refresh_token", oldRefresh), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // вход по новому паролю работает
        assertThat(post("/api/v1/auth/login", login(email, "NewPass99"), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void forgot_returns_same_response_for_unknown_account() {
        String known = uniqueEmail();
        register(known, null);
        HttpStatus forKnown = (HttpStatus) post("/api/v1/auth/password/forgot", body("login", known), null).getStatusCode();
        HttpStatus forUnknown = (HttpStatus) post("/api/v1/auth/password/forgot",
                body("login", "nobody-" + UUID.randomUUID() + "@x.test"), null).getStatusCode();
        assertThat(forKnown).isEqualTo(HttpStatus.ACCEPTED).isEqualTo(forUnknown);
    }

    @Test
    void change_password_requires_current_and_updates() {
        String email = uniqueEmail();
        String access = accessFrom(register(email, null));

        assertThat(post("/api/v1/auth/password/change",
                json.createObjectNode().put("current_password", "wrong-one").put("new_password", "NewPass99").toString(), access)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(post("/api/v1/auth/password/change",
                json.createObjectNode().put("current_password", "Passw0rd").put("new_password", "NewPass99").toString(), access)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(post("/api/v1/auth/login", login(email, "NewPass99"), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/auth/login", login(email, "Passw0rd"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private ResponseEntity<String> register(String email, String phone) {
        var node = json.createObjectNode();
        if (email != null) {
            node.put("email", email);
        }
        if (phone != null) {
            node.put("phone", phone);
        }
        node.put("password", "Passw0rd").put("first_name", "A").put("last_name", "B");
        return post("/api/v1/auth/register", node.toString(), null);
    }

    private ResponseEntity<String> post(String path, String body, String access) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (access != null) {
            h.setBearerAuth(access);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> get(String path) {
        return rest.getForEntity(path, String.class);
    }

    private String capturedEmailBody() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notifications, atLeastOnce()).sendEmail(anyString(), anyString(), body.capture());
        return body.getValue();
    }

    private String capturedSms() {
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(notifications, atLeastOnce()).sendSms(anyString(), msg.capture());
        return msg.getValue();
    }

    private static String extract(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        assertThat(m.find()).as("pattern %s in %s", pattern, text).isTrue();
        return m.group(1);
    }

    private String body(String key, String value) {
        return json.createObjectNode().put(key, value).toString();
    }

    private String login(String login, String password) {
        return json.createObjectNode().put("login", login).put("password", password).toString();
    }

    private String accessFrom(ResponseEntity<String> response) {
        return field(response, "access_token");
    }

    private String field(ResponseEntity<String> response, String name) {
        try {
            return json.readTree(response.getBody()).get(name).asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String uniqueEmail() {
        return "rec-" + UUID.randomUUID().toString().substring(0, 8) + "@tzevabest.test";
    }
}
