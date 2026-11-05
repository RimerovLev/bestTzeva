package com.tzevabest.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.tzevabest.common.web.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Проверка токена Cloudflare Turnstile перед платной покраской: гейт по человечности прямо в точке
 * траты денег. Выключен (secret пуст) — пропускаем всех (капча добавляется позже без пересборки).
 * Токен одноразовый, живёт недолго; при провале — 403, OpenAI не зовём.
 */
@Service
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final TurnstileProperties props;
    private final RestClient client;

    public TurnstileVerifier(TurnstileProperties props) {
        this.props = props;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /**
     * Проверяет токен; при провале бросает 403. Выключенная капча — всегда ок. remoteIp необязателен
     * (Turnstile сверяет его, если задан).
     */
    public void verify(String token, String remoteIp) {
        if (!props.isEnabled()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException("ai.captcha_required", HttpStatus.FORBIDDEN);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", props.getSecretKey());
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }
        boolean success;
        try {
            JsonNode res = client.post()
                    .uri(props.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            success = res != null && res.path("success").asBoolean(false);
            if (!success && res != null) {
                log.warn("Turnstile verify failed: {}", res.path("error-codes"));
            }
        } catch (RestClientException e) {
            // Сбой связи с Cloudflare не должен ронять фичу наглухо, но и пропускать нельзя —
            // считаем проверку непройденной (расход на OpenAI важнее доступности капчи).
            log.warn("Turnstile verify error: {}", e.getClass().getSimpleName());
            throw new BusinessException("ai.captcha_failed", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!success) {
            throw new BusinessException("ai.captcha_failed", HttpStatus.FORBIDDEN);
        }
    }
}
