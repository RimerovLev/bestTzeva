package com.tzevabest.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Фоновое удаление просроченных фото заявок (T-14.2). Отключено в test-профиле. */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class PhotoRetentionScheduler {

    private final PrivacyService privacy;

    @Scheduled(fixedDelayString = "${app.privacy.photo-purge-sweep:PT24H}")
    public void purge() {
        int deleted = privacy.purgeExpiredPhotos();
        if (deleted > 0) {
            log.info("Photo retention: purged {} photo(s)", deleted);
        }
    }
}
