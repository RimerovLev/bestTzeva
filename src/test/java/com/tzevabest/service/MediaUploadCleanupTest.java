package com.tzevabest.service;

import com.tzevabest.service.service.MediaUploadCleanupService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** R-67: осиротевшие presigned-загрузки (выдан ключ, но не привязан к заявке) убираются из S3 и таблицы. */
class MediaUploadCleanupTest extends IntegrationTest {

    @Autowired
    private MediaUploadCleanupService cleanup;
    @Autowired
    private S3Client s3;

    @BeforeEach
    void bucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket("tzevabest-private").build());
        } catch (SdkException ignored) {
        }
    }

    @Test
    void unused_upload_older_than_a_day_is_purged_from_storage_and_table() {
        String orphanKey = "service-requests/" + UUID.randomUUID() + ".jpg";
        String usedKey = "service-requests/" + UUID.randomUUID() + ".jpg";
        // клиент загрузил объект по presigned URL, но заявку не создал
        s3.putObject(PutObjectRequest.builder().bucket("tzevabest-private").key(orphanKey).build(),
                RequestBody.fromBytes("img".getBytes()));

        // осиротевшая запись — старше суток; привязанная (used_at) — тоже старая, но её трогать нельзя
        jdbcTemplate.update("insert into media_upload_intents (object_key, ip, created_at) "
                + "values (?, '1.2.3.4', now() - interval '2 days')", orphanKey);
        jdbcTemplate.update("insert into media_upload_intents (object_key, ip, created_at, used_at) "
                + "values (?, '1.2.3.4', now() - interval '2 days', now())", usedKey);

        int purged = cleanup.purgeOrphans();
        assertThat(purged).isEqualTo(1);

        // осиротевшая строка удалена, объект в S3 удалён
        Integer orphanLeft = jdbcTemplate.queryForObject(
                "select count(*) from media_upload_intents where object_key = ?", Integer.class, orphanKey);
        assertThat(orphanLeft).isZero();
        Throwable gone = catchThrowable(() -> s3.headObject(
                HeadObjectRequest.builder().bucket("tzevabest-private").key(orphanKey).build()));
        assertThat(gone).isInstanceOf(SdkException.class);

        // привязанная (использованная) — сохранена
        Integer usedLeft = jdbcTemplate.queryForObject(
                "select count(*) from media_upload_intents where object_key = ?", Integer.class, usedKey);
        assertThat(usedLeft).isEqualTo(1);
    }

    @Test
    void recent_unused_upload_is_kept() {
        String key = "service-requests/" + UUID.randomUUID() + ".jpg";
        jdbcTemplate.update("insert into media_upload_intents (object_key, ip) values (?, '1.2.3.4')", key);

        assertThat(cleanup.purgeOrphans()).isZero(); // моложе суток — не трогаем
        Integer left = jdbcTemplate.queryForObject(
                "select count(*) from media_upload_intents where object_key = ?", Integer.class, key);
        assertThat(left).isEqualTo(1);
    }
}
