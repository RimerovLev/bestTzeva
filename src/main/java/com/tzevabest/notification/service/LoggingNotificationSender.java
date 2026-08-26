package com.tzevabest.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Заглушка отправки — пишет в лог. Реальную реализацию добавит модуль уведомлений
 * (пометит её {@code @Primary} или исключит эту заглушку из сканирования).
 */
@Slf4j
@Component
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("[EMAIL stub] to={} subject={} body={}", to, subject, body);
    }

    @Override
    public void sendSms(String to, String message) {
        log.info("[SMS stub] to={} message={}", to, message);
    }
}
