package com.tzevabest.service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Фоновая уборка осиротевших presigned-загрузок фото (R-67). Отключена в test-профиле. */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class MediaUploadCleanupScheduler {

    private final MediaUploadCleanupService cleanup;

    @Scheduled(fixedDelayString = "${app.service.upload-intent-sweep:PT1H}")
    public void purge() {
        int deleted = cleanup.purgeOrphans();
        if (deleted > 0) {
            log.info("Media upload cleanup: purged {} orphan upload(s)", deleted);
        }
    }
}
