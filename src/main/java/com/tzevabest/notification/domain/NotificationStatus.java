package com.tzevabest.notification.domain;

/** Статус отправки. varchar + check ck_notifications_log_status. SENDING — транзитный (R-57). */
public enum NotificationStatus { QUEUED, SENDING, SENT, FAILED }
