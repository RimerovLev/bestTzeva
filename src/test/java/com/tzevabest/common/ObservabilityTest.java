package com.tzevabest.common;

import com.tzevabest.support.IntegrationTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** T-14.1: метрики в prometheus-формате и health-эндпоинт с проверками. */
@AutoConfigureObservability // включает метрики/prometheus в тесте (Boot Test их отключает)
class ObservabilityTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private S3Client s3;

    @BeforeEach
    void bucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket("test-media").build());
        } catch (SdkException exists) {
            // уже создан
        }
    }

    @Test
    void prometheus_endpoint_exposes_metrics() {
        ResponseEntity<String> resp = rest.getForEntity("/actuator/prometheus", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("# HELP").contains("jvm_");
    }

    @Test
    void health_reports_components_up() {
        ResponseEntity<String> resp = rest.getForEntity("/actuator/health", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
    }
}
