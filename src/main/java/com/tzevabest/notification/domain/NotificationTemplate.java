package com.tzevabest.notification.domain;

/**
 * Каталог шаблонов уведомлений (T-12.1). Каждый шаблон знает свой канал по умолчанию и базовый ключ
 * сообщений ({@code <keyStem>.subject} / {@code <keyStem>.body} в messages*.properties — «отдельные
 * файлы на локаль»). Часть кодов переиспользует уже существующие ключи, чтобы не дублировать текст.
 */
public enum NotificationTemplate {

    PRODUCT_REQUEST_RECEIVED("product_request_received", NotificationChannel.EMAIL, "notification.product_request.created", false),
    PRODUCT_REQUEST_CONTACTED("product_request_contacted", NotificationChannel.EMAIL, "notification.product_request.contacted", false),
    PRODUCT_REQUEST_RESERVED("product_request_reserved", NotificationChannel.EMAIL, "notification.product_request.reserved", false),
    PRODUCT_REQUEST_EXPIRED("product_request_expired", NotificationChannel.EMAIL, "notification.product_request_expired", false),
    SERVICE_REQUEST_RECEIVED("service_request_received", NotificationChannel.EMAIL, "notification.service_request_received", false),
    SERVICE_REQUEST_QUOTED("service_request_quoted", NotificationChannel.EMAIL, "notification.service_request_quoted", false),
    MEASUREMENT_REMINDER("measurement_reminder", NotificationChannel.SMS, "notification.measurement_reminder", false),
    PASSWORD_RESET("password_reset", NotificationChannel.EMAIL, "notification.password_reset", false),
    EMAIL_VERIFICATION("email_verification", NotificationChannel.EMAIL, "notification.email_verification", false),
    OTP_CODE("otp_code", NotificationChannel.SMS, "notification.otp_code", false),
    ACCOUNT_LOCKED("account_locked", NotificationChannel.EMAIL, "notification.account_locked", false),
    TOKEN_REPLAY("token_replay", NotificationChannel.EMAIL, "notification.token_replay", false),
    LOW_STOCK_ALERT("low_stock_alert", NotificationChannel.EMAIL, "notification.low_stock_alert", true),
    NEW_REQUEST_ADMIN("new_request_admin", NotificationChannel.EMAIL, "notification.new_request_admin", true);

    private final String code;
    private final NotificationChannel channel;
    private final String keyStem;
    private final boolean internal;

    NotificationTemplate(String code, NotificationChannel channel, String keyStem, boolean internal) {
        this.code = code;
        this.channel = channel;
        this.keyStem = keyStem;
        this.internal = internal;
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
