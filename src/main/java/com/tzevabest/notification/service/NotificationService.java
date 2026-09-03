package com.tzevabest.notification.service;

import com.tzevabest.notification.domain.NotificationChannel;
import com.tzevabest.notification.domain.NotificationLog;
import com.tzevabest.notification.domain.NotificationPriority;
import com.tzevabest.notification.domain.NotificationStatus;
import com.tzevabest.notification.dto.NotificationTemplate;
import com.tzevabest.notification.dto.NotificationModel;
import com.tzevabest.notification.dto.RenderedMessage;
import com.tzevabest.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.Year;
import java.util.Locale;
import java.util.Set;

/**
 * Фасад уведомлений (T-12.1): по коду шаблона рендерит тему/тело на нужной локали (Thymeleaf-вёрстка
 * email с RTL для иврита, текст для SMS/WhatsApp), выбирает канал по настройке шаблона, отправляет и
 * пишет снимок в {@code notifications_log}. Текст — в бандлах messages*.properties (отдельные файлы
 * на локаль), подстановки — из строго типизированной модели.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private static final String DEFAULT_LOCALE = "he";
    private static final Set<String> LOCALES = Set.of("he", "ru", "en");
    private static final Set<String> RTL_LANGS = Set.of("he", "iw", "ar");
    private static final String EMAIL_TEMPLATE = "notifications/email";
    private static final String COMPANY = "Tzeva Best";

    private final TemplateEngine templateEngine;
    private final MessageSource messages;
    private final NotificationLogRepository logs;
    private final JdbcTemplate jdbc;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final WhatsappSender whatsappSender;
    private final UnsubscribeService unsubscribe;
    private final NotificationMetrics metrics;

    /** Рендерит сообщение без отправки — точка для тестов и предпросмотра. */
    public RenderedMessage render(NotificationTemplate template, String localeParam, NotificationModel model) {
        return render(template, localeParam, model, null);
    }

    /**
     * Рендерит сообщение. Для нетранзакционных (информационных) email в подвал добавляется ссылка
     * отписки на {@code recipient} (T-12.3, R-61); для транзакционных писем и при неизвестном
     * получателе ссылка не ставится, и блок в шаблоне не появляется.
     */
    public RenderedMessage render(NotificationTemplate template, String localeParam, NotificationModel model,
                                  String recipient) {
        Locale locale = Locale.forLanguageTag(resolveLocale(localeParam));
        Object[] args = model.args();
        String bodyText = messages.getMessage(template.keyStem() + ".body", args, locale);

        if (template.channel() == NotificationChannel.EMAIL) {
            String subject = messages.getMessage(template.keyStem() + ".subject", args, locale);
            Context ctx = new Context(locale);
            ctx.setVariable("subject", subject);
            ctx.setVariable("body", bodyText);
            ctx.setVariable("dir", isRtl(locale) ? "rtl" : "ltr");
            ctx.setVariable("align", isRtl(locale) ? "right" : "left");
            ctx.setVariable("company", COMPANY);
            ctx.setVariable("year", Year.now().getValue());
            if (!template.transactional() && recipient != null) {
                ctx.setVariable("unsubscribeUrl", unsubscribe.unsubscribeLink(recipient));
            }
            String html = templateEngine.process(EMAIL_TEMPLATE, ctx);
            return new RenderedMessage(NotificationChannel.EMAIL, subject, html);
        }
        // SMS/WhatsApp — простой текст без HTML.
        return new RenderedMessage(template.channel(), null, bodyText);
    }

    /**
     * Рендерит, отправляет по каналу шаблона синхронно и пишет запись в журнал. Ошибка канала не
     * пробрасывается наверх. При временном сбое (не {@link NonRetryableNotificationException})
     * сообщение НЕ хоронится, а ставится в очередь ({@code QUEUED}, {@code next_attempt_at = now})
     * с приоритетом шаблона — фоновый обработчик доретраит его (для IMMEDIATE — секундными
     * задержками, R-58). Так самые критичные уведомления (OTP, сброс пароля) тоже получают ретраи.
     */
    @Transactional
    public void send(NotificationTemplate template, String recipient, String localeParam, NotificationModel model) {
        if (unsubscribe.suppresses(template.transactional(), recipient)) {
            return; // получатель отписался от нетранзакционных рассылок
        }
        RenderedMessage rendered = render(template, localeParam, model, recipient);

        NotificationLog entry = new NotificationLog();
        entry.setChannel(template.channel());
        entry.setTemplateCode(template.code());
        entry.setRecipient(recipient);
        entry.setLocale(resolveLocale(localeParam));
        entry.setSubject(rendered.subject());
        entry.setBodySnapshot(rendered.body());
        entry.setPriority(template.priority().name());
        entry.setAttemptCount(1);
        long started = System.currentTimeMillis();
        try {
            entry.setProviderMessageId(dispatch(template.channel(), recipient, rendered));
            entry.setStatus(NotificationStatus.SENT);
            entry.setSentAt(Instant.now());
            metrics.recordSent(template.channel().name(), System.currentTimeMillis() - started);
        } catch (NonRetryableNotificationException e) {
            entry.setStatus(NotificationStatus.FAILED);
            entry.setErrorMessage(e.getMessage());
            metrics.recordFailed(template.channel().name());
            log.warn("Notification {} to {} rejected (non-retryable)", template.code(), recipient, e);
        } catch (RuntimeException e) {
            entry.setStatus(NotificationStatus.QUEUED);
            entry.setNextAttemptAt(Instant.now());
            entry.setErrorMessage(e.getMessage());
            metrics.recordRetry(template.channel().name());
            log.warn("Notification {} to {} failed synchronously, queued for retry", template.code(), recipient, e);
        }
        logs.save(entry);
    }

    /**
     * Маршрутизация по приоритету (T-12.2): IMMEDIATE (OTP, сброс пароля) уходит немедленно, остальное
     * ставится в очередь (transactional outbox) — отправит фоновый обработчик после коммита.
     */
    public void notify(NotificationTemplate template, String recipient, String localeParam,
                       NotificationModel model, String referenceId) {
        if (template.priority() == NotificationPriority.IMMEDIATE) {
            send(template, recipient, localeParam, model);
        } else {
            enqueue(template, recipient, localeParam, model, referenceId);
        }
    }

    /**
     * Ставит уведомление в очередь в ТЕКУЩЕЙ транзакции (outbox): запись коммитится вместе с
     * бизнес-операцией, поэтому переживёт рестарт между коммитом и отправкой. Дубли по одному событию
     * отсекаются уникальным ключом идемпотентности через {@code on conflict do nothing} — без аборта
     * бизнес-транзакции.
     */
    public void enqueue(NotificationTemplate template, String recipient, String localeParam,
                        NotificationModel model, String referenceId) {
        enqueueAt(template, recipient, localeParam, model, referenceId, null);
    }

    /**
     * Отложенная постановка в очередь: обработчик отправит не раньше {@code sendAt} (для напоминаний —
     * T-11.3). {@code sendAt == null} — отправлять сразу (now).
     */
    public void enqueueAt(NotificationTemplate template, String recipient, String localeParam,
                          NotificationModel model, String referenceId, java.time.Instant sendAt) {
        if (unsubscribe.suppresses(template.transactional(), recipient)) {
            return; // получатель отписался от нетранзакционных рассылок
        }
        RenderedMessage rendered = render(template, localeParam, model, recipient);
        String idempotencyKey = referenceId == null ? null
                : template.code() + ":" + recipient + ":" + referenceId;
        java.sql.Timestamp nextAttempt = sendAt == null ? null : java.sql.Timestamp.from(sendAt);
        jdbc.update("""
                insert into notifications_log (channel, template_code, recipient, locale, subject,
                        body_snapshot, status, priority, attempt_count, next_attempt_at, idempotency_key)
                values (?, ?, ?, ?, ?, ?, 'QUEUED', ?, 0, coalesce(?, now()), ?)
                on conflict (idempotency_key) do nothing""",
                template.channel().name(), template.code(), recipient, resolveLocale(localeParam),
                rendered.subject(), rendered.body(), template.priority().name(), nextAttempt, idempotencyKey);
    }

    private String dispatch(NotificationChannel channel, String recipient, RenderedMessage rendered) {
        return switch (channel) {
            case EMAIL -> emailSender.send(recipient, rendered.subject(), rendered.body());
            case SMS -> smsSender.send(recipient, rendered.body());
            case WHATSAPP -> whatsappSender.send(recipient, rendered.body());
            case PUSH -> throw new UnsupportedOperationException("PUSH channel is not implemented");
        };
    }

    private static boolean isRtl(Locale locale) {
        return RTL_LANGS.contains(locale.getLanguage());
    }

    /** Локаль к he/ru/en; неизвестная/пустая → he. */
    private static String resolveLocale(String localeParam) {
        if (localeParam != null) {
            String lower = localeParam.toLowerCase(Locale.ROOT);
            if (LOCALES.contains(lower)) {
                return lower;
            }
        }
        return DEFAULT_LOCALE;
    }
}
