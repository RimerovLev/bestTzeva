package com.tzevabest.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Защита «на отказ»: при любом профиле, кроме {@code local}/{@code test} (prod, незаданный → default,
 * опечатка в имени профиля), приложение не стартует, если какой-либо секрет оставлен небезопасным
 * дефолтом из репозитория. Так забытый {@code SPRING_PROFILES_ACTIVE} не поднимет прод с dev-секретом.
 * <p>
 * Секреты без дефолта ({@code JWT_SECRET} и пр.) и так уронили бы старт неразрешённым плейсхолдером.
 * Но {@code SPAM_SIGNING_SECRET} и {@code PRIVACY_PSEUDONYM_SECRET} имеют dev-дефолты в
 * {@code application.yml}; забыв переменную окружения, прод молча поднялся бы с публично известным
 * секретом (подделка токенов антиспам-формы, предсказуемые псевдонимы анонимизации). Этот guard ловит
 * такой случай явной ошибкой на старте, а заодно отвергает слишком короткие секреты.
 */
@Component
@Profile("!local & !test")
@RequiredArgsConstructor
public class ProdSecretsGuard {

    /** Маркеры дефолтных значений из application.yml — в prod недопустимы. */
    private static final String[] INSECURE_MARKERS = {"dev-only", "insecure", "change-me", "changeme"};
    private static final int MIN_SECRET_LENGTH = 24;

    @Value("${app.jwt.secret}")
    private String jwtSecret;
    @Value("${app.notification.webhook-secret}")
    private String webhookSecret;
    @Value("${app.notification.unsubscribe-secret}")
    private String unsubscribeSecret;
    @Value("${app.spam.signing-secret}")
    private String spamSecret;
    @Value("${app.privacy.pseudonym-secret}")
    private String pseudonymSecret;

    @PostConstruct
    void verify() {
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("app.jwt.secret", jwtSecret);
        secrets.put("app.notification.webhook-secret", webhookSecret);
        secrets.put("app.notification.unsubscribe-secret", unsubscribeSecret);
        secrets.put("app.spam.signing-secret", spamSecret);
        secrets.put("app.privacy.pseudonym-secret", pseudonymSecret);

        secrets.forEach((name, value) -> {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Secret " + name + " must be set in prod");
            }
            String lower = value.toLowerCase();
            for (String marker : INSECURE_MARKERS) {
                if (lower.contains(marker)) {
                    throw new IllegalStateException(
                            "Secret " + name + " still uses an insecure default value in prod — set it from the environment");
                }
            }
            if (value.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "Secret " + name + " is too short for prod (min " + MIN_SECRET_LENGTH + " chars)");
            }
        });
    }
}
