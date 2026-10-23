package com.tzevabest.notification.service.whatsapp;

import java.util.List;

/**
 * Шаблонное сообщение WhatsApp: имя approved-шаблона, языковой код и значения параметров тела
 * ({{1}}, {{2}}, …) по порядку. Свободного текста нет — так требует WhatsApp для сообщений,
 * инициированных бизнесом.
 */
public record WhatsappTemplateMessage(String templateName, String languageCode, List<String> bodyParams) {
}
