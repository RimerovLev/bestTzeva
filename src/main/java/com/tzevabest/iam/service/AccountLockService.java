package com.tzevabest.iam.service;

import com.tzevabest.iam.repository.UserRepository;
import com.tzevabest.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Прогрессивная блокировка аккаунта: после {@value MAX_FAILURES} неудачных входов подряд аккаунт
 * блокируется на {@value LOCK_MINUTES} минут (Redis-ключ с TTL). Счётчик сбрасывается при успешном
 * входе. При блокировке владельцу уходит уведомление.
 */
@Component
@RequiredArgsConstructor
public class AccountLockService {

    private static final int MAX_FAILURES = 10;
    private static final long LOCK_MINUTES = 15;
    private static final Duration LOCK = Duration.ofMinutes(LOCK_MINUTES);
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final NotificationSender notifications;

    public boolean isLocked(long userId) {
        return Boolean.TRUE.equals(redis.hasKey(lockKey(userId)));
    }

    public long lockTtlSeconds(long userId) {
        Long ttl = redis.getExpire(lockKey(userId));
        return (ttl == null || ttl < 0) ? LOCK.toSeconds() : ttl;
    }

    public void recordFailure(long userId) {
        Long count = redis.opsForValue().increment(failKey(userId));
        if (count != null && count == 1L) {
            redis.expire(failKey(userId), FAIL_WINDOW);
        }
        if (count != null && count >= MAX_FAILURES) {
            redis.opsForValue().set(lockKey(userId), "1", LOCK);
            redis.delete(failKey(userId));
            notifyOwner(userId);
        }
    }

    public void reset(long userId) {
        redis.delete(failKey(userId));
    }

    private void notifyOwner(long userId) {
        users.findById(userId).ifPresent(user -> {
            String message = "Your Tzeva Best account was temporarily locked for "
                    + LOCK_MINUTES + " minutes after multiple failed login attempts.";
            if (user.getEmail() != null) {
                notifications.sendEmail(user.getEmail(), "Tzeva Best — account locked", message);
            } else if (user.getPhone() != null) {
                notifications.sendSms(user.getPhone(), message);
            }
        });
    }

    private static String lockKey(long userId) {
        return "auth:lock:" + userId;
    }

    private static String failKey(long userId) {
        return "auth:login-fails:" + userId;
    }
}
