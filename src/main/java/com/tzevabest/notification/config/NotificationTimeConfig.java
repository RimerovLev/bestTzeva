package com.tzevabest.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Часы для политик времени уведомлений (тихие часы). Переопределяются в тестах фиксированным Clock. */
@Configuration
public class NotificationTimeConfig {

    @Bean
    Clock notificationClock() {
        return Clock.systemUTC();
    }
}
