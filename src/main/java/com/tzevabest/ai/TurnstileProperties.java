package com.tzevabest.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare Turnstile — капча перед платной покраской. Secret только из окружения. Пусто → проверка
 * выключена (локалка/бета до появления ключей работают без капчи, как и OpenAI-ключ).
 */
@ConfigurationProperties("app.turnstile")
public class TurnstileProperties {

    /** Секрет для siteverify (Bearer не нужен — уходит в теле). Пусто → капча выключена. */
    private String secretKey = "";
    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private int timeoutSeconds = 8;

    public boolean isEnabled() {
        return secretKey != null && !secretKey.isBlank();
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getVerifyUrl() {
        return verifyUrl;
    }

    public void setVerifyUrl(String verifyUrl) {
        this.verifyUrl = verifyUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
