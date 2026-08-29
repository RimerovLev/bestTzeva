package com.tzevabest.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Заглушка SMS до подключения провайдера (T-12.3). */
@Slf4j
@Component
public class LoggingSmsSender implements SmsSender {
    @Override
    public void send(String to, String message) {
        log.info("[SMS stub] to={} message={}", to, message);
    }
}
