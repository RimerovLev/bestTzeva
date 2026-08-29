package com.tzevabest.notification.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Метрики уведомлений в actuator (T-12.2): размер очереди (gauge), доставленные/неудачные по каналам
 * (counters), время доставки (timer).
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this.registry = registry;
        registry.gauge("notifications.queue.size", jdbc, j -> {
            Long queued = j.queryForObject(
                    "select count(*) from notifications_log where status = 'QUEUED'", Long.class);
            return queued == null ? 0 : queued;
        });
    }

    public void recordSent(String channel, long millis) {
        Counter.builder("notifications.sent").tag("channel", channel).register(registry).increment();
        Timer.builder("notifications.delivery").tag("channel", channel).register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordFailed(String channel) {
        Counter.builder("notifications.failed").tag("channel", channel).register(registry).increment();
    }

    public void recordRetry(String channel) {
        Counter.builder("notifications.retried").tag("channel", channel).register(registry).increment();
    }
}
