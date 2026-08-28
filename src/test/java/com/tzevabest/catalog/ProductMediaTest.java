package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * T-4.7: хранилище медиа на S3/MinIO. Загрузка кладёт объект в бакет и строку product_media,
 * витрина отдаёт публичную ссылку, единственность главного сохраняется, удаление сносит и объект,
 * и строку.
 */
class ProductMediaTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;
    @Autowired
    private S3Client s3;

    @BeforeEach
    void ensureBucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket("test-media").build());
        } catch (SdkException alreadyExists) {
            // бакет уже создан прошлым тестом — норма
        }
    }

    @Test
    void upload_stores_object_and_row_and_card_exposes_url() throws Exception {
        long productId = seedProduct();

        JsonNode uploaded = json.readTree(uploadProductMedia(productId, "first.png", false).getBody());
        String url = uploaded.get("url").asText();
        long mediaId = uploaded.get("id").asLong();
        assertThat(url).contains("test-media");

        // объект физически в бакете
        String objectKey = jdbcTemplate.queryForObject(
                "select object_key from product_media where id = ?", String.class, mediaId);
        assertThat(objectKey).isNotNull();
        var head = s3.headObject(HeadObjectRequest.builder().bucket("test-media").key(objectKey).build());
        assertThat(head.contentLength()).isPositive();

        // витрина отдаёт медиа
        JsonNode card = json.readTree(rest.getForEntity("/api/v1/products/" + productId, String.class).getBody());
        assertThat(card.get("media")).hasSize(1);
        assertThat(card.get("media").get(0).get("url").asText()).isEqualTo(url);
    }

    @Test
    void second_primary_unsets_the_first() throws Exception {
        long productId = seedProduct();
        uploadProductMedia(productId, "a.png", true);
        uploadProductMedia(productId, "b.png", true);

        Integer primaries = jdbcTemplate.queryForObject(
                "select count(*) from product_media where product_id = ? and is_primary", Integer.class, productId);
        assertThat(primaries).isEqualTo(1); // ровно одно главное
    }

    @Test
    void delete_removes_object_and_row() throws Exception {
        long productId = seedProduct();
        JsonNode uploaded = json.readTree(uploadProductMedia(productId, "c.png", false).getBody());
        long mediaId = uploaded.get("id").asLong();
        String objectKey = jdbcTemplate.queryForObject(
                "select object_key from product_media where id = ?", String.class, mediaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(managerToken());
        ResponseEntity<String> del = rest.exchange("/api/v1/admin/media/" + mediaId, HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from product_media where id = ?", Integer.class, mediaId);
        assertThat(rows).isZero();

        Throwable gone = catchThrowable(() -> s3.headObject(
                HeadObjectRequest.builder().bucket("test-media").key(objectKey).build()));
        assertThat(gone).isInstanceOf(SdkException.class); // объекта больше нет → head падает
    }

    @Test
    void non_image_content_type_is_rejected() {
        long productId = seedProduct();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(managerToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource("not-an-image".getBytes()) {
            @Override
            public String getFilename() {
                return "note.txt";
            }
        });
        // type=IMAGE, но content-type text/plain → 422
        ResponseEntity<String> resp = rest.exchange("/api/v1/admin/products/" + productId + "/media?type=IMAGE",
                HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ResponseEntity<String> uploadProductMedia(long productId, String filename, boolean primary) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(managerToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(PNG_1x1) {
            @Override
            public String getFilename() {
                return filename;
            }
            @Override
            public long contentLength() {
                return PNG_1x1.length;
            }
        });
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/admin/products/" + productId + "/media?type=IMAGE&primary=" + primary,
                HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp;
    }

    private String managerToken() {
        return jwt.generateAccess(1L, "MANAGER", "m@tzevabest.test", "he");
    }

    private long seedProduct() {
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('c-media-" + System.nanoTime() + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b-media-" + System.nanoTime() + "','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                        insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                        values (?, ?, 'P-MEDIA-%d', 'INTERIOR','INDOOR','MATTE','WATER', true) returning id"""
                        .formatted(System.nanoTime()),
                Long.class, categoryId, brandId);
        jdbcTemplate.update("insert into product_translations (product_id, locale, name) values (?, 'he', 'צבע')", productId);
        return productId;
    }

    // Минимальный валидный PNG 1×1 (content-type image/png навешивается multipart-конвертером по .png).
    private static final byte[] PNG_1x1 = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
}
