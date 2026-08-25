package com.tzevabest.notification.domain;

/** Статус отправки. varchar + check ck_notifications_log_status. */
public enum NotificationStatus { QUEUED, SENT, FAILED }
