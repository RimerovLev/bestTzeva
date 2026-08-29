package com.tzevabest.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Заглушка email до подключения провайдера (T-12.3). */
@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {
    @Override
    public void send(String to, String subject, String htmlBody) {
        log.info("[EMAIL stub] to={} subject={} bytes={}", to, subject, htmlBody == null ? 0 : htmlBody.length());
    }
}
