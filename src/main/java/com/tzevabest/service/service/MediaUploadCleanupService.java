package com.tzevabest.service.service;

import com.tzevabest.common.lock.DistributedLock;
import com.tzevabest.media.service.MediaStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Уборка осиротевших presigned-загрузок фото (R-67): объекты, для которых выдавался presigned PUT, но
 * которые не привязались к заявке, удаляются из приватного бакета и из media_upload_intents по
 * истечении срока. Под распределённой блокировкой (как остальные фоновые задачи, R-56).
 * <p>
 * Порядок как в ретеншене фото: сначала объект в S3, потом строка — сбой приводит к повтору, а не к
 * потере ссылки на объект.
 */
@Service
@Slf4j
public class MediaUploadCleanupService {

    private static final String LOCK_KEY = "media:upload-intents:purge:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final MediaStorage mediaStorage;
    private final DistributedLock lock;

    @Value("${app.service.upload-intent-retention:P1D}")
    private Duration retention;

    public MediaUploadCleanupService(JdbcTemplate jdbc, MediaStorage mediaStorage, DistributedLock lock) {
        this.jdbc = jdbc;
        this.mediaStorage = mediaStorage;
        this.lock = lock;
    }

    /** Удаляет неиспользованные загрузки старше срока (объект в S3 и строку). Возвращает число удалённых. */
    @Transactional
    public int purgeOrphans() {
        return lock.runIfAcquired(LOCK_KEY, LOCK_TTL, handle -> {
            long seconds = retention.getSeconds();
            List<Map<String, Object>> orphans = jdbc.queryForList(
                    "select id, object_key from media_upload_intents "
                            + "where used_at is null and created_at < now() - (? || ' seconds')::interval", seconds);
            for (Map<String, Object> row : orphans) {
                String key = (String) row.get("object_key");
                if (key != null) {
                    mediaStorage.deletePrivate(key); // объект в приватном бакете, не только строка
                }
                jdbc.update("delete from media_upload_intents where id = ?", row.get("id"));
            }
            if (!orphans.isEmpty()) {
                log.info("Media upload cleanup: purged {} orphan upload(s)", orphans.size());
            }
            return orphans.size();
        }, 0);
    }
}
