package com.tzevabest.notification.service;

/** Публичный интерфейс отправки уведомлений. Реальные каналы добавит модуль notification. */
public interface NotificationSender {

    void sendEmail(String to, String subject, String body);

    void sendSms(String to, String message);
}
