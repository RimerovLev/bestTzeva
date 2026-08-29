package com.tzevabest.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Заглушка WhatsApp до подключения провайдера (T-12.3). */
@Slf4j
@Component
public class LoggingWhatsappSender implements WhatsappSender {
    @Override
    public void send(String to, String message) {
        log.info("[WHATSAPP stub] to={} message={}", to, message);
    }
}
