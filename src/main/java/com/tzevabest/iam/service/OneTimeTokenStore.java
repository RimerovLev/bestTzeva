package com.tzevabest.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Одноразовые токены (подтверждение email, сброс пароля) в Redis. Хранится SHA-256 хеш
 * токена → userId с TTL; {@code consume} атомарно читает и удаляет (GETDEL) — повторное
 * использование невозможно, истёкший токен = отсутствующий ключ.
 */
@Component
@RequiredArgsConstructor
public class OneTimeTokenStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redis;

    public String issue(String namespace, long userId, Duration ttl) {
        String token = randomToken();
        redis.opsForValue().set(key(namespace, token), Long.toString(userId), ttl);
        return token;
    }

    public Optional<Long> consume(String namespace, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redis.opsForValue().getAndDelete(key(namespace, token));
        return value == null ? Optional.empty() : Optional.of(Long.parseLong(value));
    }

    private static String key(String namespace, String token) {
        return "auth:" + namespace + ":" + sha256(token);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
