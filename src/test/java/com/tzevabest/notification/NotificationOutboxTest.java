package com.tzevabest.notification;

import com.tzevabest.notification.domain.NotificationTemplate;
import com.tzevabest.notification.dto.OtpModel;
import com.tzevabest.notification.dto.ProductRequestModel;
import com.tzevabest.notification.service.EmailSender;
import com.tzevabest.notification.service.NonRetryableNotificationException;
import com.tzevabest.notification.service.NotificationDispatcher;
import com.tzevabest.notification.service.NotificationService;
import com.tzevabest.notification.service.SmsSender;
import com.tzevabest.notification.service.WhatsappSender;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-12.2: transactional outbox. Уведомление переживает «рестарт» между коммитом бизнес-операции и
 * отправкой; два параллельных обработчика не шлют дубли; ретраи с задержкой и финальный FAILED;
 * немедленный приоритет мимо очереди; дедупликация по ключу идемпотентности.
 */
@Import(NotificationOutboxTest.SendersConfig.class)
class NotificationOutboxTest extends IntegrationTest {

    @Autowired
    private NotificationService notifications;
    @Autowired
    private NotificationDispatcher dispatcher;
    @Autowired
    private ControllableSender sender;

    @BeforeEach
    void resetSender() {
        sender.reset();
    }

    @Test
    void message_survives_crash_between_commit_and_send() {
        // Бизнес-операция закоммитила уведомление в очередь, но отправка не произошла (как при рестарте).
        notifications.enqueue(NotificationTemplate.PRODUCT_REQUEST_RECEIVED, "a@x.test", "he",
                new ProductRequestModel("PR-1"), "PR-1");
        assertThat(status("a@x.test")).isEqualTo("QUEUED"); // персистентно, не потеряно

        int processed = dispatcher.dispatchDue();
        assertThat(processed).isEqualTo(1);
        assertThat(status("a@x.test")).isEqualTo("SENT");   // обработчик доставил позже
        assertThat(sender.total()).isEqualTo(1);
    }

    @Test
    void two_parallel_processors_do_not_send_duplicates() throws Exception {
        int n = 20;
        for (int i = 0; i < n; i++) {
            notifications.enqueue(NotificationTemplate.OTP_CODE, "+97250000" + String.format("%04d", i), "he",
                    new OtpModel("111111"), "otp-" + i);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> tasks = List.of(dispatcher::processBatch, dispatcher::processBatch);
        int totalProcessed = 0;
        for (Future<Integer> f : pool.invokeAll(tasks)) {
            totalProcessed += f.get();
        }
        pool.shutdown();

        assertThat(totalProcessed).isEqualTo(n);   // каждая строка взята ровно раз (SKIP LOCKED)
        assertThat(sender.total()).isEqualTo(n);   // каждая отправлена ровно раз
        assertThat(sender.maxPerRecipient()).isEqualTo(1);
        Long queued = jdbcTemplate.queryForObject(
                "select count(*) from notifications_log where status = 'QUEUED'", Long.class);
        assertThat(queued).isZero();
    }

    @Test
    void retryable_failure_schedules_backoff_then_fails_after_max() {
        sender.mode = Mode.RETRYABLE;
        notifications.enqueue(NotificationTemplate.OTP_CODE, "+972501112233", "he",
                new OtpModel("222222"), "retry-1");

        dispatcher.processBatch();
        var row = jdbcTemplate.queryForMap("select status, attempt_count, next_attempt_at > now() as future "
                + "from notifications_log where idempotency_key = 'otp_code:+972501112233:retry-1'");
        assertThat(row.get("status")).isEqualTo("QUEUED");
        assertThat(row.get("attempt_count")).isEqualTo(1);
        assertThat(row.get("future")).isEqualTo(true); // отложено на бэкофф

        // доводим до максимума попыток
        jdbcTemplate.update("update notifications_log set attempt_count = 4, next_attempt_at = now() "
                + "where idempotency_key = 'otp_code:+972501112233:retry-1'");
        dispatcher.processBatch();
        var failed = jdbcTemplate.queryForMap("select status, attempt_count from notifications_log "
                + "where idempotency_key = 'otp_code:+972501112233:retry-1'");
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(failed.get("attempt_count")).isEqualTo(5);
    }

    @Test
    void non_retryable_failure_fails_immediately() {
        sender.mode = Mode.NON_RETRYABLE;
        notifications.enqueue(NotificationTemplate.OTP_CODE, "+972500000000", "he",
                new OtpModel("333333"), "nr-1");
        dispatcher.processBatch();
        var row = jdbcTemplate.queryForMap("select status, attempt_count from notifications_log "
                + "where idempotency_key = 'otp_code:+972500000000:nr-1'");
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempt_count")).isEqualTo(1); // без ретраев
    }

    @Test
    void immediate_priority_sends_without_queue_normal_is_queued() {
        // OTP — IMMEDIATE: уходит сразу, строка сразу SENT
        notifications.notify(NotificationTemplate.OTP_CODE, "+972501234567", "he",
                new OtpModel("444444"), "imm-1");
        assertThat(sender.total()).isEqualTo(1);
        assertThat(status("+972501234567")).isEqualTo("SENT");

        // NORMAL — в очередь, ещё не отправлено
        notifications.notify(NotificationTemplate.PRODUCT_REQUEST_RECEIVED, "b@x.test", "he",
                new ProductRequestModel("PR-2"), "PR-2");
        assertThat(status("b@x.test")).isEqualTo("QUEUED");
        assertThat(sender.total()).isEqualTo(1); // ещё не доставлено
    }

    @Test
    void duplicate_enqueue_is_deduplicated() {
        notifications.enqueue(NotificationTemplate.PRODUCT_REQUEST_RECEIVED, "c@x.test", "he",
                new ProductRequestModel("PR-3"), "PR-3");
        notifications.enqueue(NotificationTemplate.PRODUCT_REQUEST_RECEIVED, "c@x.test", "he",
                new ProductRequestModel("PR-3"), "PR-3"); // тот же ключ идемпотентности
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from notifications_log where recipient = 'c@x.test'", Long.class);
        assertThat(count).isEqualTo(1);
    }

    private String status(String recipient) {
        return jdbcTemplate.queryForObject(
                "select status from notifications_log where recipient = ? order by id desc limit 1",
                String.class, recipient);
    }

    // ── управляемая заглушка каналов ──────────────────────────────────────────────

    enum Mode { OK, RETRYABLE, NON_RETRYABLE }

    static class ControllableSender implements EmailSender, SmsSender, WhatsappSender {
        volatile Mode mode = Mode.OK;
        private final AtomicInteger total = new AtomicInteger();
        private final ConcurrentHashMap<String, AtomicInteger> perRecipient = new ConcurrentHashMap<>();

        void reset() {
            mode = Mode.OK;
            total.set(0);
            perRecipient.clear();
        }

        int total() {
            return total.get();
        }

        int maxPerRecipient() {
            return perRecipient.values().stream().mapToInt(AtomicInteger::get).max().orElse(0);
        }

        private void handle(String to) {
            switch (mode) {
                case RETRYABLE -> throw new RuntimeException("temporary channel error");
                case NON_RETRYABLE -> throw new NonRetryableNotificationException("invalid recipient");
                case OK -> {
                    total.incrementAndGet();
                    perRecipient.computeIfAbsent(to, k -> new AtomicInteger()).incrementAndGet();
                }
            }
        }

        @Override
        public void send(String to, String subject, String htmlBody) {
            handle(to);
        }

        @Override
        public void send(String to, String message) {
            handle(to);
        }
    }

    @TestConfiguration
    static class SendersConfig {
        // Один @Primary-бин типа ControllableSender покрывает все три интерфейса канала (он их реализует)
        // и выигрывает у логирующих заглушек при инъекции по каждому из них.
        @Bean
        @Primary
        ControllableSender controllableSender() {
            return new ControllableSender();
        }
    }
}
