package com.tzevabest.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Заглушка email до подключения провайдера (T-12.3). */
@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {
    @Override
    public String send(String to, String subject, String htmlBody) {
        String id = "log-" + UUID.randomUUID();
        log.info("[EMAIL stub] to={} subject={} bytes={} id={}", to, subject,
                htmlBody == null ? 0 : htmlBody.length(), id);
        return id;
    }
}
