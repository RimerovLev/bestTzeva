package com.tzevabest.notification;

import com.tzevabest.notification.domain.NotificationChannel;
import com.tzevabest.notification.domain.NotificationPriority;
import com.tzevabest.notification.service.QuietHoursPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** T-12.3: информационные SMS откладываются в тихие часы (21:00–08:00 Израиля); OTP и email — нет. */
class QuietHoursPolicyTest {

    private static final ZoneId TZ = ZoneId.of("Asia/Jerusalem");

    private QuietHoursPolicy at(int hour) {
        Instant fixed = ZonedDateTime.of(2026, 1, 15, hour, 0, 0, 0, TZ).toInstant();
        return new QuietHoursPolicy(Clock.fixed(fixed, TZ));
    }

    @Test
    void informational_sms_at_night_is_deferred_to_morning() {
        var defer = at(22).deferUntil(NotificationChannel.SMS, NotificationPriority.NORMAL);
        assertThat(defer).isPresent();
        // отложено до 08:00 следующего дня (Израиль)
        Instant expected = ZonedDateTime.of(2026, 1, 16, 8, 0, 0, 0, TZ).toInstant();
        assertThat(defer.get()).isEqualTo(expected);
    }

    @Test
    void early_morning_sms_deferred_to_same_day_eight() {
        var defer = at(3).deferUntil(NotificationChannel.SMS, NotificationPriority.NORMAL);
        assertThat(defer).isPresent();
        assertThat(defer.get()).isEqualTo(ZonedDateTime.of(2026, 1, 15, 8, 0, 0, 0, TZ).toInstant());
    }

    @Test
    void daytime_sms_is_not_deferred() {
        assertThat(at(12).deferUntil(NotificationChannel.SMS, NotificationPriority.NORMAL)).isEmpty();
    }

    @Test
    void otp_and_email_are_never_deferred() {
        assertThat(at(22).deferUntil(NotificationChannel.SMS, NotificationPriority.IMMEDIATE)).isEmpty();
        assertThat(at(22).deferUntil(NotificationChannel.EMAIL, NotificationPriority.NORMAL)).isEmpty();
    }
}
