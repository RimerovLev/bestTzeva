package com.tzevabest.notification.service;

/** Канал SMS. Реальную реализацию добавит T-12.3. */
public interface SmsSender {
    void send(String to, String message);
}
