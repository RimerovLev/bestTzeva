package com.tzevabest.iam.service;

import com.tzevabest.iam.repository.UserRepository;
import com.tzevabest.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Учёт подозрительной активности по аккаунту: считает неудачные входы подряд по {@code userId} и при
 * достижении порога {@value MAX_FAILURES} за окно уведомляет владельца. Счётчик сбрасывается при
 * успешном входе.
 * <p>
 * Сервис НЕ влияет на HTTP-ответ и не блокирует вход: троттлинг перебора и ответ 429 живут на
 * {@code @RateLimit} по строке логина и по IP. Проверка по {@code userId} здесь срабатывала бы только
 * для существующих аккаунтов и превратилась бы в оракул их существования (см. R-04), поэтому единственная
 * задача сервиса — уведомление, для чего ему и нужен {@code userId}.
 */
@Component
@RequiredArgsConstructor
public class AccountLockService {

    private static final int MAX_FAILURES = 10;
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(15);

    /**
     * Атомарно инкрементирует счётчик и на первом инкременте ставит TTL окна. Одной командой, чтобы
     * счётчик не остался вечным при обрыве между INCR и EXPIRE (иначе окно бы не истекало).
     * KEYS[1]=счётчик, ARGV[1]=ttlMs. Возвращает текущее значение.
     */
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[1])) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final NotificationSender notifications;

    /** Зафиксировать неудачный вход; на каждом {@value MAX_FAILURES}-м за окно — уведомить владельца. */
    public void recordFailure(long userId) {
        Long count = redis.execute(INCR_WITH_TTL, List.of(failKey(userId)),
                Long.toString(FAIL_WINDOW.toMillis()));
        if (count != null && count >= MAX_FAILURES) {
            redis.delete(failKey(userId)); // перевзвод окна: следующая серия снова уведомит
            notifyOwner(userId);
        }
    }

    public void reset(long userId) {
        redis.delete(failKey(userId));
    }

    private void notifyOwner(long userId) {
        users.findById(userId).ifPresent(user -> {
            String message = "Multiple failed login attempts were detected on your Tzeva Best account.";
            if (user.getEmail() != null) {
                notifications.sendEmail(user.getEmail(), "Tzeva Best — suspicious sign-in activity", message);
            } else if (user.getPhone() != null) {
                notifications.sendSms(user.getPhone(), message);
            }
        });
    }

    private static String failKey(long userId) {
        return "auth:login-fails:" + userId;
    }
}
