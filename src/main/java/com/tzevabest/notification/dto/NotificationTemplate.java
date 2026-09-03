package com.tzevabest.notification.dto;

import com.tzevabest.notification.domain.NotificationChannel;
import com.tzevabest.notification.domain.NotificationPriority;

/**
 * Каталог шаблонов уведомлений (T-12.1). Каждый шаблон знает свой канал по умолчанию и базовый ключ
 * сообщений ({@code <keyStem>.subject} / {@code <keyStem>.body} в messages*.properties — «отдельные
 * файлы на локаль»). Часть кодов переиспользует уже существующие ключи, чтобы не дублировать текст.
 */
public enum NotificationTemplate {

    PRODUCT_REQUEST_RECEIVED("product_request_received", NotificationChannel.EMAIL, "notification.product_request.created", false, true, NotificationPriority.NORMAL),
    PRODUCT_REQUEST_CONTACTED("product_request_contacted", NotificationChannel.EMAIL, "notification.product_request.contacted", false, true, NotificationPriority.NORMAL),
    PRODUCT_REQUEST_RESERVED("product_request_reserved", NotificationChannel.EMAIL, "notification.product_request.reserved", false, true, NotificationPriority.NORMAL),
    PRODUCT_REQUEST_EXPIRED("product_request_expired", NotificationChannel.EMAIL, "notification.product_request_expired", false, true, NotificationPriority.NORMAL),
    SERVICE_REQUEST_RECEIVED("service_request_received", NotificationChannel.EMAIL, "notification.service_request_received", false, true, NotificationPriority.NORMAL),
    SERVICE_REQUEST_QUOTED("service_request_quoted", NotificationChannel.EMAIL, "notification.service_request_quoted", false, true, NotificationPriority.NORMAL),
    MEASUREMENT_REMINDER("measurement_reminder", NotificationChannel.SMS, "notification.measurement_reminder", false, true, NotificationPriority.NORMAL),
    PASSWORD_RESET("password_reset", NotificationChannel.EMAIL, "notification.password_reset", false, true, NotificationPriority.IMMEDIATE),
    EMAIL_VERIFICATION("email_verification", NotificationChannel.EMAIL, "notification.email_verification", false, true, NotificationPriority.IMMEDIATE),
    OTP_CODE("otp_code", NotificationChannel.SMS, "notification.otp_code", false, true, NotificationPriority.IMMEDIATE),
    ACCOUNT_LOCKED("account_locked", NotificationChannel.EMAIL, "notification.account_locked", false, true, NotificationPriority.NORMAL),
    TOKEN_REPLAY("token_replay", NotificationChannel.EMAIL, "notification.token_replay", false, true, NotificationPriority.NORMAL),
    LOW_STOCK_ALERT("low_stock_alert", NotificationChannel.EMAIL, "notification.low_stock_alert", true, true, NotificationPriority.NORMAL),
    NEW_REQUEST_ADMIN("new_request_admin", NotificationChannel.EMAIL, "notification.new_request_admin", true, true, NotificationPriority.NORMAL),
    // Информационная рассылка клиенту — НЕтранзакционная: подавляется для отписавшихся и несёт ссылку отписки.
    MARKETING_NEWS("marketing_news", NotificationChannel.EMAIL, "notification.marketing_news", false, false, NotificationPriority.NORMAL);

    private final String code;
    private final NotificationChannel channel;
    private final String keyStem;
    private final boolean internal;
    private final boolean transactional;
    private final NotificationPriority priority;

    NotificationTemplate(String code, NotificationChannel channel, String keyStem, boolean internal,
                         boolean transactional, NotificationPriority priority) {
        this.code = code;
        this.channel = channel;
        this.keyStem = keyStem;
        this.internal = internal;
        this.transactional = transactional;
        this.priority = priority;
    }

    public NotificationPriority priority() {
        return priority;
    }

    /**
     * Транзакционные уведомления (статус заявки, безопасность, OTP, сброс пароля) не отписываются —
     * они не рекламные. Маркетинговые/информационные рассылки ({@code false}) подавляются для
     * отписавшихся и несут ссылку отписки.
     */
    public boolean transactional() {
        return transactional;
    }

    public String code() {
        return code;
    }

    public NotificationChannel channel() {
        return channel;
    }

    public String keyStem() {
        return keyStem;
    }

    /** Внутреннее уведомление персоналу (low-stock, новая заявка), не клиенту. */
    public boolean internal() {
        return internal;
    }
}
