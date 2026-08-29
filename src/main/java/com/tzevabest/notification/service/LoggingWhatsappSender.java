package com.tzevabest.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Заглушка WhatsApp до подключения провайдера (T-12.3). */
@Slf4j
@Component
public class LoggingWhatsappSender implements WhatsappSender {
    @Override
    public String send(String to, String message) {
        String id = "log-" + UUID.randomUUID();
        log.info("[WHATSAPP stub] to={} id={}", to, id);
        return id;
    }
}
