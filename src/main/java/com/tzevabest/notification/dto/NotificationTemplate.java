package com.tzevabest.notification.dto;

import com.tzevabest.notification.domain.NotificationChannel;
import com.tzevabest.notification.domain.NotificationPriority;

/**
 * Каталог шаблонов уведомлений (T-12.1). Каждый шаблон знает свой канал по умолчанию и базовый ключ
 * сообщений ({@code <keyStem>.subject} / {@code <keyStem>.body} в messages*.properties — «отдельные
 * файлы на локаль»). Часть кодов переиспользует уже существующие ключи, чтобы не дублировать текст.
 */
public enum NotificationTemplate {

    PRODUCT_REQUEST_RECEIVED("product_request_received", NotificationChannel.EMAIL, "notification.product_request.created", false, NotificationPriority.NORMAL),
    PRODUCT_REQUEST_CONTACTED("product_request_contacted", NotificationChannel.EMAIL, "notification.product_request.contacted", false, NotificationPriority.NORMAL),
    PRODUCT_REQUEST_RESERVED("product_request_reserved", NotificationChannel.EMAIL, "notification.product_request.reserved", false, NotificationPriority.NORMAL),
    PRODUCT_REQUEST_EXPIRED("product_request_expired", NotificationChannel.EMAIL, "notification.product_request_expired", false, NotificationPriority.NORMAL),
    SERVICE_REQUEST_RECEIVED("service_request_received", NotificationChannel.EMAIL, "notification.service_request_received", false, NotificationPriority.NORMAL),
    SERVICE_REQUEST_QUOTED("service_request_quoted", NotificationChannel.EMAIL, "notification.service_request_quoted", false, NotificationPriority.NORMAL),
    MEASUREMENT_REMINDER("measurement_reminder", NotificationChannel.SMS, "notification.measurement_reminder", false, NotificationPriority.NORMAL),
    PASSWORD_RESET("password_reset", NotificationChannel.EMAIL, "notification.password_reset", false, NotificationPriority.IMMEDIATE),
    EMAIL_VERIFICATION("email_verification", NotificationChannel.EMAIL, "notification.email_verification", false, NotificationPriority.IMMEDIATE),
    OTP_CODE("otp_code", NotificationChannel.SMS, "notification.otp_code", false, NotificationPriority.IMMEDIATE),
    ACCOUNT_LOCKED("account_locked", NotificationChannel.EMAIL, "notification.account_locked", false, NotificationPriority.NORMAL),
    TOKEN_REPLAY("token_replay", NotificationChannel.EMAIL, "notification.token_replay", false, NotificationPriority.NORMAL),
    LOW_STOCK_ALERT("low_stock_alert", NotificationChannel.EMAIL, "notification.low_stock_alert", true, NotificationPriority.NORMAL),
    NEW_REQUEST_ADMIN("new_request_admin", NotificationChannel.EMAIL, "notification.new_request_admin", true, NotificationPriority.NORMAL);

    private final String code;
    private final NotificationChannel channel;
    private final String keyStem;
    private final boolean internal;
    private final NotificationPriority priority;

    NotificationTemplate(String code, NotificationChannel channel, String keyStem, boolean internal,
                         NotificationPriority priority) {
        this.code = code;
        this.channel = channel;
        this.keyStem = keyStem;
        this.internal = internal;
        this.priority = priority;
    }

    public NotificationPriority priority() {
        return priority;
    }

    /**
     * Транзакционные уведомления (статус заявки, безопасность, OTP, сброс пароля) не отписываются —
     * они не рекламные. Маркетинговые/информационные рассылки вернут {@code false} и будут подавлены
     * для отписавшихся. В текущем наборе все шаблоны транзакционные.
     */
    public boolean transactional() {
        return true;
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
