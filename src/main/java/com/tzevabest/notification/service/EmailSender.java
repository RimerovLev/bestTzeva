package com.tzevabest.notification.service;

/** Канал email. Реальную реализацию (SMTP/провайдер) добавит T-12.3. */
public interface EmailSender {
    void send(String to, String subject, String htmlBody);
}
