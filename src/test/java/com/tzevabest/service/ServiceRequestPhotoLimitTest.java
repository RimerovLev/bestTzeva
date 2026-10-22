package com.tzevabest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Аудит п.8: presigned-загрузка фото заявки — whitelist типов и отбраковка великанов при привязке. */
class ServiceRequestPhotoLimitTest extends IntegrationTest {

    private static final String PRIVATE_BUCKET = "tzevabest-private";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private S3Client s3;

    @BeforeEach
    void bucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(PRIVATE_BUCKET).build());
        } catch (SdkException ignored) {
        }
    }

    @Test
    void presign_rejects_non_whitelisted_type() throws Exception {
        String body = "{\"files\":[{\"filename\":\"evil.svg\",\"content_type\":\"image/svg+xml\"}]}";
        ResponseEntity<String> resp = post("/api/v1/service-requests/photos/presign", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(resp.getBody()).get("code").asText()).isEqualTo("service_request.photo_type");
    }

    @Test
    void oversized_uploaded_photo_is_rejected_and_purged_on_attach() throws Exception {
        // 1) получаем presigned-ключ (image/jpeg — в whitelist), запись intent создаётся
        String presign = post("/api/v1/service-requests/photos/presign",
                "{\"files\":[{\"filename\":\"room.jpg\",\"content_type\":\"image/jpeg\"}]}").getBody();
        String key = json.readTree(presign).get("uploads").get(0).get("object_key").asText();

        // 2) клиент загрузил объект больше лимита (presigned PUT не ограничивает размер)
        byte[] huge = new byte[11 * 1024 * 1024];
        s3.putObject(PutObjectRequest.builder().bucket(PRIVATE_BUCKET).key(key).contentType("image/jpeg").build(),
                RequestBody.fromBytes(huge));

        // 3) привязка к заявке отбраковывает его
        String create = "{\"customer_name\":\"Dana\",\"customer_phone\":\"0501110006\",\"property_type\":\"APARTMENT\","
                + "\"consent\":true,\"photo_keys\":[\"" + key + "\"]}";
        ResponseEntity<String> resp = post("/api/v1/service-requests", create);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(resp.getBody()).get("code").asText()).isEqualTo("service_request.photo_rejected");

        // 4) объект удалён из бакета, строки медиа нет
        Throwable gone = catchThrowable(() -> s3.headObject(
                HeadObjectRequest.builder().bucket(PRIVATE_BUCKET).key(key).build()));
        assertThat(gone).isInstanceOf(SdkException.class);
        Integer media = jdbcTemplate.queryForObject("select count(*) from service_request_media", Integer.class);
        assertThat(media).isZero();
    }

    private ResponseEntity<String> post(String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }
}
