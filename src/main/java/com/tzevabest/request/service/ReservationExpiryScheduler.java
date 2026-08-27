package com.tzevabest.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Фоновая уборка просроченных резервов. Отключена в тестовом профиле — там уборка вызывается
 * из теста напрямую, чтобы не зависеть от планировщика.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {

    private final ReservationExpiryService expiry;

    @Scheduled(fixedDelayString = "${app.inventory.reservation-sweep}")
    public void sweep() {
        int expired = expiry.sweep();
        if (expired > 0) {
            log.info("Reservation sweep: {} request(s) expired to LOST", expired);
        }
    }
}
