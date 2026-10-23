package com.tzevabest.notification.service.whatsapp;

import com.tzevabest.notification.service.NonRetryableNotificationException;

import java.util.List;

/**
 * Собирает {@link WhatsappTemplateMessage} по коду уведомления, локали и параметрам: имя approved
 * WhatsApp-шаблона и языковой код берутся из {@link WhatsappProperties}. Код без сопоставленного
 * шаблона — не-ретраибельная ошибка (в логе будет виден, но не зациклит ретраи).
 */
public class WhatsappMessages {

    private final WhatsappProperties props;

    public WhatsappMessages(WhatsappProperties props) {
        this.props = props;
    }

    public WhatsappTemplateMessage resolve(String templateCode, String locale, List<String> params) {
        String name = props.getTemplates().get(templateCode);
        if (name == null) {
            throw new NonRetryableNotificationException("No WhatsApp template mapped for code " + templateCode);
        }
        String lang = props.getLanguages().getOrDefault(locale, "en_US");
        return new WhatsappTemplateMessage(name, lang, params == null ? List.of() : params);
    }
}
