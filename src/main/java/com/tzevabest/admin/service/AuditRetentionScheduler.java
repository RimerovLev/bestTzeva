package com.tzevabest.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Фоновый ретеншен аудита. Отключён в test-профиле (вызывается из теста напрямую). */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class AuditRetentionScheduler {

    private final AuditRetentionService retention;

    @Scheduled(fixedDelayString = "${app.audit.sweep:PT24H}")
    public void purge() {
        int deleted = retention.purge();
        if (deleted > 0) {
            log.info("Audit retention: purged {} record(s)", deleted);
        }
    }
}
