package com.tzevabest.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.support.IntegrationTest;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Троттлинг входа по строке логина (R-04): защита от перебора и ответ 429 не зависят от существования
 * аккаунта, поэтому не служат оракулом. Клиент без авто-ретраев — иначе HttpClient5 уснул бы на
 * Retry-After окна в 15 минут.
 */
class LoginThrottleTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    private RestTemplate rest;

    @BeforeEach
    void initClient() {
        var factory = new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableAutomaticRetries().build());
        rest = new RestTemplate(factory);
        rest.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
    }

    @Test
    void throttle_response_is_identical_for_existing_and_nonexistent_login() throws Exception {
        // существующий аккаунт: 5 неудач, 6-я попытка — 429
        String existing = "u-" + token() + "@tzevabest.test";
        register(existing, "Passw0rd");
        ResponseEntity<String> existingBlocked = exhaustFailures(existing);

        flushRedis(); // очищаем окна (в т.ч. по IP), чтобы вторая серия не упёрлась в лимит IP

        // несуществующий аккаунт: та же серия
        ResponseEntity<String> unknownBlocked = exhaustFailures("ghost-" + token() + "@x.test");

        // статус, код, тело и Retry-After совпадают — по ответу аккаунты неразличимы
        assertThat(existingBlocked.getStatusCode().value()).isEqualTo(429);
        assertThat(unknownBlocked.getStatusCode().value()).isEqualTo(429);
        assertThat(code(existingBlocked)).isEqualTo("common.rate_limited");
        assertThat(code(unknownBlocked)).isEqualTo("common.rate_limited");
        String retryExisting = existingBlocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        String retryUnknown = unknownBlocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryExisting).isNotNull();
        assertThat(retryUnknown).isEqualTo(retryExisting);
    }

    @Test
    void five_successful_logins_do_not_trigger_throttle() throws Exception {
        String email = "u-" + token() + "@tzevabest.test";
        register(email, "Passw0rd");
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "Passw0rd").getStatusCode().value())
                    .as("успешный вход #%d не должен троттлиться", i + 1)
                    .isEqualTo(200);
        }
    }

    /** Пять неудач подряд по одному логину; возвращает ответ на 6-ю попытку (ожидается 429). */
    private ResponseEntity<String> exhaustFailures(String login) throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login(login, "WrongPass9").getStatusCode().value())
                    .as("неудача #%d — ещё 401, не 429", i + 1)
                    .isEqualTo(401);
        }
        return login(login, "WrongPass9");
    }

    private ResponseEntity<String> register(String email, String password) throws Exception {
        String body = json.createObjectNode()
                .put("email", email).put("password", password)
                .put("first_name", "A").put("last_name", "B").toString();
        return post("/api/v1/auth/register", body);
    }

    private ResponseEntity<String> login(String login, String password) throws Exception {
        String body = json.createObjectNode().put("login", login).put("password", password).toString();
        return post("/api/v1/auth/login", body);
    }

    private ResponseEntity<String> post(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("http://localhost:" + port + path,
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String code(ResponseEntity<String> response) throws Exception {
        JsonNode node = json.readTree(response.getBody());
        return node.hasNonNull("code") ? node.get("code").asText() : null;
    }

    private void flushRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
