package com.tzevabest.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

/**
 * База для REST-интеграционных тестов: настроенный {@link TestRestTemplate} (реальный
 * HTTP на случайном порту) и хелперы для запросов «от лица роли».
 * <p>
 * <b>Допущение харнеса:</b> модуль аутентификации (JWT/IAM) ещё не реализован, поэтому
 * {@link #bearerToken(TestRole)} возвращает заглушку. Когда появится выдача токенов,
 * достаточно переопределить этот метод — сигнатуры хелперов останутся прежними.
 */
public abstract class RestIntegrationTest extends IntegrationTest {

    @Autowired
    protected TestRestTemplate rest;

    /** Роли, от лица которых выполняются запросы в тестах. */
    protected enum TestRole {
        ANONYMOUS, CUSTOMER, SPECIALIST, ADMIN
    }

    /** Заголовки запроса с токеном роли (для ANONYMOUS — без Authorization). */
    protected HttpHeaders authHeaders(TestRole role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (role != null && role != TestRole.ANONYMOUS) {
            headers.setBearerAuth(bearerToken(role));
        }
        return headers;
    }

    /**
     * Токен для роли. Заглушка до появления IAM — переопределите в наследнике/харнесе,
     * когда появится реальная выдача JWT.
     */
    protected String bearerToken(TestRole role) {
        return "test-token." + role.name().toLowerCase(Locale.ROOT);
    }

    // ── Хелперы запросов ────────────────────────────────────────────────────────

    protected <T> ResponseEntity<T> exchangeAs(TestRole role, HttpMethod method,
                                               String path, Object body, Class<T> responseType) {
        return rest.exchange(path, method, new HttpEntity<>(body, authHeaders(role)), responseType);
    }

    protected <T> ResponseEntity<T> getAs(TestRole role, String path, Class<T> responseType) {
        return exchangeAs(role, HttpMethod.GET, path, null, responseType);
    }

    protected <T> ResponseEntity<T> postAs(TestRole role, String path, Object body, Class<T> responseType) {
        return exchangeAs(role, HttpMethod.POST, path, body, responseType);
    }

    protected <T> ResponseEntity<T> putAs(TestRole role, String path, Object body, Class<T> responseType) {
        return exchangeAs(role, HttpMethod.PUT, path, body, responseType);
    }

    protected <T> ResponseEntity<T> deleteAs(TestRole role, String path, Class<T> responseType) {
        return exchangeAs(role, HttpMethod.DELETE, path, null, responseType);
    }
}
