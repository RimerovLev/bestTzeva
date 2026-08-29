package com.tzevabest.notification.domain;

/**
 * Приоритет отправки. IMMEDIATE (OTP, сброс пароля) уходит немедленно вне общей очереди; NORMAL —
 * через очередь пачкой.
 */
public enum NotificationPriority { IMMEDIATE, NORMAL }
