package com.tzevabest.notification.service;

/** Канал WhatsApp. Возвращает id сообщения у провайдера, может быть null. */
public interface WhatsappSender {
    String send(String to, String message);
}
