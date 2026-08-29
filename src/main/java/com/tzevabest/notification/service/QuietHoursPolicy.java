package com.tzevabest.notification.service;

import com.tzevabest.notification.domain.NotificationChannel;
import com.tzevabest.notification.domain.NotificationPriority;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Тихие часы (T-12.3): информационные SMS не уходят с 21:00 до 08:00 по израильскому времени —
 * откладываются до утра. OTP и прочие IMMEDIATE-уведомления идут всегда. Email не ограничивается.
 */
@Component
@RequiredArgsConstructor
public class QuietHoursPolicy {

    private static final ZoneId TZ = ZoneId.of("Asia/Jerusalem");
    private static final LocalTime QUIET_START = LocalTime.of(21, 0);
    private static final LocalTime QUIET_END = LocalTime.of(8, 0);

    private final Clock clock;

    /** До какого момента отложить отправку, если сейчас тихие часы; иначе пусто (отправлять сейчас). */
    public Optional<Instant> deferUntil(NotificationChannel channel, NotificationPriority priority, Instant now) {
        if (channel != NotificationChannel.SMS || priority == NotificationPriority.IMMEDIATE) {
            return Optional.empty();
        }
        if (isQuiet(now)) {
            return Optional.of(nextMorning(now));
        }
        return Optional.empty();
    }

    public Optional<Instant> deferUntil(NotificationChannel channel, NotificationPriority priority) {
        return deferUntil(channel, priority, clock.instant());
    }

    boolean isQuiet(Instant at) {
        LocalTime t = at.atZone(TZ).toLocalTime();
        // Окно пересекает полночь: [21:00, 24:00) ∪ [00:00, 08:00).
        return !t.isBefore(QUIET_START) || t.isBefore(QUIET_END);
    }

    Instant nextMorning(Instant at) {
        ZonedDateTime z = at.atZone(TZ);
        ZonedDateTime morning = z.toLocalTime().isBefore(QUIET_END)
                ? z.toLocalDate().atTime(QUIET_END).atZone(TZ)          // ещё до 08:00 — сегодня утром
                : z.toLocalDate().plusDays(1).atTime(QUIET_END).atZone(TZ); // вечер — завтра утром
        return morning.toInstant();
    }
}
