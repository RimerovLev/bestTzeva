package com.tzevabest.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Планировщик разбора очереди уведомлений. Отключён в тестовом профиле — там обработка вызывается из
 * теста напрямую. Распределённая блокировка — внутри {@link NotificationDispatcher#dispatchDue()}.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchScheduler {

    private final NotificationDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${app.notification.dispatch-interval:PT30S}")
    public void dispatch() {
        int processed = dispatcher.dispatchDue();
        if (processed > 0) {
            log.debug("Notification dispatch: processed {} message(s)", processed);
        }
    }
}
