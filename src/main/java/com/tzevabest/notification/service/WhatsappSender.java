package com.tzevabest.notification.service;

/** Канал WhatsApp. Реальную реализацию добавит T-12.3. */
public interface WhatsappSender {
    void send(String to, String message);
}
