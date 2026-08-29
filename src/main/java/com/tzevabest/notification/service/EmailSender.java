package com.tzevabest.notification.service;

/** Канал email. Возвращает id сообщения у провайдера (для сопоставления вебхука доставки), может быть null. */
public interface EmailSender {
    String send(String to, String subject, String htmlBody);
}
