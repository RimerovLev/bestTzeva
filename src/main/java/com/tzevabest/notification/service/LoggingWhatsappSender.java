package com.tzevabest.notification.service;

import com.tzevabest.notification.service.whatsapp.WhatsappTemplateMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/** Заглушка WhatsApp, когда канал выключен (app.notification.whatsapp.enabled=false). */
@Slf4j
public class LoggingWhatsappSender implements WhatsappSender {
    @Override
    public String send(String to, WhatsappTemplateMessage message) {
        String id = "log-" + UUID.randomUUID();
        log.info("[WHATSAPP stub] to={} template={} lang={} params={} id={}",
                to, message.templateName(), message.languageCode(), message.bodyParams(), id);
        return id;
    }
}
