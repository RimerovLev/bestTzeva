package com.tzevabest.notification.service;

/** Канал SMS. Возвращает id сообщения у провайдера, может быть null. */
public interface SmsSender {
    String send(String to, String message);
}
