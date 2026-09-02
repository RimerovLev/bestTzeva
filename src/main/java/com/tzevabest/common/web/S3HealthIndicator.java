package com.tzevabest.common.web;

import com.tzevabest.media.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/** Health-проверка доступности S3-хранилища медиа (T-14.1). */
@Component("s3")
@ConditionalOnEnabledHealthIndicator("s3")
@RequiredArgsConstructor
public class S3HealthIndicator implements HealthIndicator {

    private final S3Client s3;
    private final StorageProperties props;

    @Override
    public Health health() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
            return Health.up().withDetail("bucket", props.bucket()).build();
        } catch (RuntimeException e) {
            return Health.down(e).withDetail("bucket", props.bucket()).build();
        }
    }
}
