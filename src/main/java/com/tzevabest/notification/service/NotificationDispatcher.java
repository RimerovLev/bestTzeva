package com.tzevabest.notification.service;

import com.tzevabest.common.lock.DistributedLock;
import com.tzevabest.notification.domain.NotificationChannel;
import com.tzevabest.notification.domain.NotificationPriority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Фоновый обработчик очереди уведомлений (transactional outbox, T-12.2).
 * <p>
 * <b>Захват и отправка разделены (R-57).</b> В короткой транзакции пачка помечается {@code SENDING}
 * с {@code FOR UPDATE SKIP LOCKED} (два процесса берут непересекающиеся строки) и коммитится —
 * блокировки строк и соединение из пула не держатся на время сетевых вызовов. Отправка и запись
 * результата идут вне транзакции, по одному сообщению, каждое своим коротким {@code update}: сбой
 * между отправкой и записью повторит максимум одно сообщение, а не всю пачку. Зависшие {@code SENDING}
 * старше таймаута возвращаются в {@code QUEUED} отдельным проходом.
 * <p>
 * Весь проход — под распределённой блокировкой ({@link DistributedLock}) с проверкой владельца при
 * снятии и продлением по ходу (R-56). Проход ограничен по числу пачек, чтобы укладываться в TTL.
 * <p>
 * Ретраи с экспоненциальной задержкой: NORMAL — {@value #MAX_ATTEMPTS} попыток (1/5/15/60 мин),
 * IMMEDIATE (OTP, сброс пароля) — {@value #IMMEDIATE_MAX_ATTEMPTS} попыток с секундными задержками
 * (10/30/60 с: минуты для OTP бессмысленны, код истечёт, R-58).
 * {@link NonRetryableNotificationException} (неверный адрес) не ретраится.
 */
@Service
@Slf4j
public class NotificationDispatcher {

    static final int BATCH = 25;
    static final int MAX_BATCHES = 40;
    static final int MAX_ATTEMPTS = 5;
    static final int IMMEDIATE_MAX_ATTEMPTS = 4;
    private static final int[] BACKOFF_MINUTES = {1, 5, 15, 60};       // NORMAL: после попытки 1..4
    private static final int[] IMMEDIATE_BACKOFF_SECONDS = {10, 30, 60}; // IMMEDIATE: после попытки 1..3
    private static final String LOCK_KEY = "notifications:dispatch:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);
    private static final Duration SENDING_TIMEOUT = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final DistributedLock lock;
    private final TransactionTemplate tx;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final WhatsappSender whatsappSender;
    private final NotificationMetrics metrics;
    private final QuietHoursPolicy quietHours;

    public NotificationDispatcher(JdbcTemplate jdbc, DistributedLock lock, PlatformTransactionManager txManager,
                                  EmailSender emailSender, SmsSender smsSender, WhatsappSender whatsappSender,
                                  NotificationMetrics metrics, QuietHoursPolicy quietHours) {
        this.jdbc = jdbc;
        this.lock = lock;
        this.tx = new TransactionTemplate(txManager);
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.whatsappSender = whatsappSender;
        this.metrics = metrics;
        this.quietHours = quietHours;
    }

    /** Один проход обработки под распределённой блокировкой. Возвращает число обработанных строк. */
    public int dispatchDue() {
        return lock.runIfAcquired(LOCK_KEY, LOCK_TTL, handle -> {
            requeueStuck();
            String owner = UUID.randomUUID().toString();
            int total = 0;
            for (int batch = 0; batch < MAX_BATCHES; batch++) {
                int processed = processClaimed(claim(owner));
                total += processed;
                if (processed < BATCH || !handle.extend()) {
                    break; // очередь исчерпана, либо владение блокировкой утеряно — выходим
                }
            }
            return total;
        }, 0);
    }

    /**
     * Одна пачка: захват в короткой транзакции ({@code SENDING}) плюс отправка вне транзакции.
     * Точка для конкурентных тестов (SKIP LOCKED в захвате разводит инстансы без внешней блокировки).
     */
    public int processBatch() {
        return processClaimed(claim(UUID.randomUUID().toString()));
    }

    /** Фаза 1: помечает готовые строки {@code SENDING} в своей транзакции и возвращает их (RETURNING). */
    private List<Row> claim(String owner) {
        return tx.execute(status -> jdbc.query("""
                update notifications_log set status = 'SENDING', locked_by = ?, locked_at = now(),
                        updated_at = now()
                where id in (
                    select id from notifications_log
                    where status = 'QUEUED' and next_attempt_at <= now()
                    order by next_attempt_at, id
                    limit ? for update skip locked)
                returning id, channel, recipient, subject, body_snapshot, attempt_count, created_at, priority""",
                (rs, n) -> mapRow(rs), owner, BATCH));
    }

    /** Фаза 2: отправляет каждую захваченную строку вне транзакции, своим коротким апдейтом. */
    private int processClaimed(List<Row> rows) {
        for (Row row : rows) {
            deliver(row);
        }
        return rows.size();
    }

    /** Возвращает зависшие в {@code SENDING} (упавший процесс) обратно в очередь. */
    private void requeueStuck() {
        int requeued = jdbc.update("""
                update notifications_log set status = 'QUEUED', locked_by = null, locked_at = null,
                        updated_at = now()
                where status = 'SENDING' and locked_at < now() - (? || ' seconds')::interval""",
                SENDING_TIMEOUT.getSeconds());
        if (requeued > 0) {
            log.warn("Requeued {} notification(s) stuck in SENDING", requeued);
        }
    }

    private void deliver(Row row) {
        NotificationChannel channel = NotificationChannel.valueOf(row.channel);
        NotificationPriority priority = NotificationPriority.valueOf(row.priority);
        // Тихие часы (T-12.3): информационные SMS ночью откладываются до утра, попытка не тратится.
        var defer = quietHours.deferUntil(channel, priority, Instant.now());
        if (defer.isPresent()) {
            jdbc.update("update notifications_log set status = 'QUEUED', next_attempt_at = ?, "
                    + "locked_by = null, locked_at = null, updated_at = now() where id = ?",
                    java.sql.Timestamp.from(defer.get()), row.id);
            return;
        }
        int attempt = row.attemptCount + 1;
        try {
            String providerId = send(row);
            long latency = Math.max(0, System.currentTimeMillis() - row.createdAt.toEpochMilli());
            jdbc.update("update notifications_log set status = 'SENT', sent_at = now(), attempt_count = ?, "
                    + "provider_message_id = ?, error_message = null, locked_by = null, locked_at = null, "
                    + "updated_at = now() where id = ?", attempt, providerId, row.id);
            metrics.recordSent(row.channel, latency);
        } catch (NonRetryableNotificationException e) {
            markFailed(row, attempt, e.getMessage());
        } catch (RuntimeException e) {
            boolean immediate = priority == NotificationPriority.IMMEDIATE;
            int maxAttempts = immediate ? IMMEDIATE_MAX_ATTEMPTS : MAX_ATTEMPTS;
            if (attempt >= maxAttempts) {
                markFailed(row, attempt, e.getMessage());
                log.error("Notification {} to {} failed permanently after {} attempts",
                        row.id, row.recipient, attempt, e);
            } else {
                int[] backoff = immediate ? IMMEDIATE_BACKOFF_SECONDS : BACKOFF_MINUTES;
                int delay = backoff[Math.min(attempt - 1, backoff.length - 1)];
                String unit = immediate ? "seconds" : "minutes";
                jdbc.update("update notifications_log set status = 'QUEUED', attempt_count = ?, error_message = ?, "
                        + "next_attempt_at = now() + (? || ' " + unit + "')::interval, "
                        + "locked_by = null, locked_at = null, updated_at = now() where id = ?",
                        attempt, e.getMessage(), delay, row.id);
                metrics.recordRetry(row.channel);
            }
        }
    }

    private void markFailed(Row row, int attempt, String error) {
        jdbc.update("update notifications_log set status = 'FAILED', attempt_count = ?, error_message = ?, "
                + "locked_by = null, locked_at = null, updated_at = now() where id = ?", attempt, error, row.id);
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
