package com.tzevabest.notification.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Журнал отправленных уведомлений. Таблица {@code notifications_log}. */
@Entity
@Table(name = "notifications_log")
@Getter
@Setter
public class NotificationLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "template_code", nullable = false, length = 120)
    private String templateCode;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "locale", length = 5)
    private String locale;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body_snapshot")
    private String bodySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.QUEUED;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Когда попытка отправки готова к исполнению (для очереди и ретраев). */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** Ключ идемпотентности: templateCode:recipient:referenceId. Уникален, защищает от дублей. */
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "priority", nullable = false, length = 16)
    private String priority = "NORMAL";
}
