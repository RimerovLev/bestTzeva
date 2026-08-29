package com.tzevabest.notification.service;

import com.tzevabest.notification.domain.NotificationChannel;
import com.tzevabest.notification.domain.NotificationPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Фоновый обработчик очереди уведомлений (transactional outbox, T-12.2).
 * <p>
 * Забирает готовые к отправке ({@code status='QUEUED' and next_attempt_at <= now()}) пачкой с
 * {@code FOR UPDATE SKIP LOCKED}: два процесса берут непересекающиеся строки и не шлют дубли. Весь
 * проход — под распределённой блокировкой в Redis ({@code SET NX} с TTL), как уборка резервов по
 * R-42. Ретраи с экспоненциальной задержкой ({@value #MAX_ATTEMPTS} попыток, задержки
 * 1/5/15/60 мин); {@link NonRetryableNotificationException} (неверный адрес) не ретраится.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationDispatcher {

    static final int BATCH = 100;
    static final int MAX_ATTEMPTS = 5;
    private static final int[] BACKOFF_MINUTES = {1, 5, 15, 60}; // задержка после попытки 1..4
    private static final String LOCK_KEY = "notifications:dispatch:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final PlatformTransactionManager txManager;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final WhatsappSender whatsappSender;
    private final NotificationMetrics metrics;
    private final QuietHoursPolicy quietHours;

    /** Один проход обработки под распределённой блокировкой. Возвращает число обработанных строк. */
    public int dispatchDue() {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return 0; // очередь уже разбирает другой инстанс
        }
        try {
            TransactionTemplate tx = new TransactionTemplate(txManager);
            int total = 0;
            int processed;
            do {
                Integer n = tx.execute(status -> processBatchInternal());
                processed = n == null ? 0 : n;
                total += processed;
            } while (processed == BATCH);
            return total;
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    /** Одна пачка в своей транзакции — точка для конкурентных тестов (SKIP LOCKED без общего лока). */
    @Transactional
    public int processBatch() {
        return processBatchInternal();
    }

    private int processBatchInternal() {
        List<Row> rows = jdbc.query("""
                select id, channel, recipient, subject, body_snapshot, attempt_count, created_at, priority
                from notifications_log
                where status = 'QUEUED' and next_attempt_at <= now()
                order by next_attempt_at, id
                limit ? for update skip locked""",
                (rs, n) -> mapRow(rs), BATCH);

        for (Row row : rows) {
            deliver(row);
        }
        return rows.size();
    }

    private void deliver(Row row) {
        NotificationChannel channel = NotificationChannel.valueOf(row.channel);
        NotificationPriority priority = NotificationPriority.valueOf(row.priority);
        // Тихие часы (T-12.3): информационные SMS ночью откладываются до утра, попытка не тратится.
        var defer = quietHours.deferUntil(channel, priority, Instant.now());
        if (defer.isPresent()) {
            jdbc.update("update notifications_log set next_attempt_at = ?, updated_at = now() where id = ?",
                    java.sql.Timestamp.from(defer.get()), row.id);
            return;
        }
        int attempt = row.attemptCount + 1;
        try {
            String providerId = send(row);
            long latency = Math.max(0, System.currentTimeMillis() - row.createdAt.toEpochMilli());
            jdbc.update("update notifications_log set status = 'SENT', sent_at = now(), attempt_count = ?, "
                    + "provider_message_id = ?, error_message = null, updated_at = now() where id = ?",
                    attempt, providerId, row.id);
            metrics.recordSent(row.channel, latency);
        } catch (NonRetryableNotificationException e) {
            markFailed(row, attempt, e.getMessage());
        } catch (RuntimeException e) {
            if (attempt >= MAX_ATTEMPTS) {
                markFailed(row, attempt, e.getMessage());
                log.error("Notification {} to {} failed permanently after {} attempts",
                        row.id, row.recipient, attempt, e);
            } else {
                int delay = BACKOFF_MINUTES[attempt - 1];
                jdbc.update("update notifications_log set attempt_count = ?, error_message = ?, "
                        + "next_attempt_at = now() + (? || ' minutes')::interval, updated_at = now() where id = ?",
                        attempt, e.getMessage(), delay, row.id);
                metrics.recordRetry(row.channel);
            }
        }
    }

    private void markFailed(Row row, int attempt, String error) {
        jdbc.update("update notifications_log set status = 'FAILED', attempt_count = ?, error_message = ?, "
                + "updated_at = now() where id = ?", attempt, error, row.id);
        metrics.recordFailed(row.channel);
    }

    private String send(Row row) {
        NotificationChannel channel = NotificationChannel.valueOf(row.channel);
        return switch (channel) {
            case EMAIL -> emailSender.send(row.recipient, row.subject, row.body);
            case SMS -> smsSender.send(row.recipient, row.body);
            case WHATSAPP -> whatsappSender.send(row.recipient, row.body);
            case PUSH -> throw new NonRetryableNotificationException("PUSH channel is not implemented");
        };
    }

    private static Row mapRow(ResultSet rs) throws java.sql.SQLException {
        Row r = new Row();
        r.id = rs.getLong("id");
        r.channel = rs.getString("channel");
        r.recipient = rs.getString("recipient");
        r.subject = rs.getString("subject");
        r.body = rs.getString("body_snapshot");
        r.attemptCount = rs.getInt("attempt_count");
        r.createdAt = rs.getTimestamp("created_at").toInstant();
        r.priority = rs.getString("priority");
        return r;
    }

    private static final class Row {
        long id;
        String channel;
        String recipient;
        String subject;
        String body;
        int attemptCount;
        Instant createdAt;
        String priority;
    }
}
