package com.tzevabest.notification.service;

import com.tzevabest.notification.service.whatsapp.WhatsappTemplateMessage;

/**
 * Канал WhatsApp. Отправляет ШАБЛОННОЕ сообщение (WhatsApp не доставляет свободный текст для
 * сообщений, инициированных бизнесом). Возвращает id сообщения у провайдера, может быть null.
 */
public interface WhatsappSender {
    String send(String to, WhatsappTemplateMessage message);
}
