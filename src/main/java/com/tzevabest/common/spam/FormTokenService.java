package com.tzevabest.common.spam;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.OptionalLong;

/**
 * Подписанный токен формы (T-11.1.1): выдаётся при открытии формы с меткой времени, чтобы отсекать
 * ботов, отправляющих заявку быстрее человека. Подпись HMAC-SHA256, время — в самом токене, состояние
 * на сервере не хранится.
 */
@Component
@RequiredArgsConstructor
public class FormTokenService {

    @Value("${app.spam.signing-secret}")
    private String secret;

    /** Выдать токен с текущей меткой времени. */
    public String issue() {
        String issuedAt = Long.toString(System.currentTimeMillis());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(issuedAt.getBytes(StandardCharsets.UTF_8));
        return payload + "." + hmac(payload);
    }

    /** Сколько секунд прошло с выдачи токена; пусто, если токен пуст/невалиден. */
    public OptionalLong ageSeconds(String token) {
        if (token == null) {
            return OptionalLong.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return OptionalLong.empty();
        }
        String payload = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        if (!matches(hmac(payload), signature)) {
            return OptionalLong.empty();
        }
        try {
            long issuedAt = Long.parseLong(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
            return OptionalLong.of((System.currentTimeMillis() - issuedAt) / 1000);
        } catch (RuntimeException e) {
            return OptionalLong.empty();
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static boolean matches(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
