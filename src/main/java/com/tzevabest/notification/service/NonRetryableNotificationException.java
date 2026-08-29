package com.tzevabest.notification.service;

/**
 * Ошибка отправки, которую бессмысленно повторять (например неверный адрес получателя). Такое
 * уведомление сразу помечается FAILED, без ретраев.
 */
public class NonRetryableNotificationException extends RuntimeException {
    public NonRetryableNotificationException(String message) {
        super(message);
    }
}
