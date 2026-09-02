package com.tzevabest.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.admin.service.PrivacyService;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** T-14.2: экспорт данных субъекта, анонимизация с сохранением заявок, ретеншен фото (с объектами S3). */
class PrivacyTest extends IntegrationTest {

    private static final String PHONE = "+972501119999";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;
    @Autowired
    private PrivacyService privacy;
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
    void export_collects_all_subject_data() throws Exception {
        long id = seedProductRequest("NEW");
        jdbcTemplate.update("insert into consents (contact, consent_type, granted, source, policy_version) "
                + "values (?, 'DATA_PROCESSING', true, 'product_request', '2026-08-01')", PHONE);
        jdbcTemplate.update("insert into notifications_log (channel, template_code, recipient, status) "
                + "values ('SMS','otp_code', ?, 'SENT')", PHONE);

        JsonNode data = json.readTree(post("/export").getBody());
        assertThat(data.get("product_requests")).isNotEmpty();
        assertThat(data.get("consents")).isNotEmpty();
        assertThat(data.get("notifications")).isNotEmpty();

        Integer audited = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'privacy.export'", Integer.class);
        assertThat(audited).isEqualTo(1);
        assertThat(id).isPositive();
    }

    @Test
    void anonymize_keeps_requests_but_removes_pii() throws Exception {
        long id = seedProductRequest("NEW");

        JsonNode res = json.readTree(post("/anonymize").getBody());
        assertThat(res.get("anonymized").asInt()).isGreaterThanOrEqualTo(1);

        // заявка сохранена, но обезличена
        var row = jdbcTemplate.queryForMap("select customer_name, customer_phone from product_requests where id = ?", id);
        assertThat(row.get("customer_name")).isEqualTo("[deleted]");
        assertThat(row.get("customer_phone")).isEqualTo("[deleted]");
        // позиции на месте (отчётность по спросу)
        Integer items = jdbcTemplate.queryForObject(
                "select count(*) from product_request_items where request_id = ?", Integer.class, id);
        assertThat(items).isEqualTo(1);
    }

    @Test
    void photo_retention_deletes_row_and_s3_object() {
        String key = "service-requests/" + UUID.randomUUID() + ".jpg";
        s3.putObject(PutObjectRequest.builder().bucket("tzevabest-private").key(key).build(),
                RequestBody.fromBytes("img".getBytes()));

        long reqId = jdbcTemplate.queryForObject("insert into service_requests "
                + "(request_number, customer_name, customer_phone, property_type, source, consent_given_at, "
                + " status, locale, updated_at) values (?, 'C', ?, 'APARTMENT', 'SITE_FORM', now(), 'COMPLETED', 'he', "
                + " now() - interval '400 days') returning id",
                Long.class, "SR-" + UUID.randomUUID().toString().substring(0, 10), PHONE);
        jdbcTemplate.update("insert into service_request_media (request_id, object_key, type) values (?, ?, 'IMAGE')",
                reqId, key);

        int purged = privacy.purgeExpiredPhotos();
        assertThat(purged).isGreaterThanOrEqualTo(1);
        Integer left = jdbcTemplate.queryForObject(
                "select count(*) from service_request_media where request_id = ?", Integer.class, reqId);
        assertThat(left).isZero();
        // объект в S3 удалён
        Throwable gone = catchThrowable(() -> s3.headObject(
                HeadObjectRequest.builder().bucket("tzevabest-private").key(key).build()));
        assertThat(gone).isInstanceOf(SdkException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long seedProductRequest(String status) {
        long id = jdbcTemplate.queryForObject("insert into product_requests "
                + "(request_number, customer_name, customer_phone, source, consent_given_at, policy_version, status) "
                + "values (?, 'Dana', ?, 'SITE_FORM', now(), '2026-08-01', ?) returning id",
                Long.class, "PR-" + UUID.randomUUID().toString().substring(0, 10), PHONE, status);
        // позиция требует вариант
        long cat = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + uid() + "') returning id", Long.class);
        long brand = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + uid() + "','B') returning id", Long.class);
        long product = jdbcTemplate.queryForObject("insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active) "
                + "values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id", Long.class, cat, brand, "P-" + uid());
        long variant = jdbcTemplate.queryForObject("insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active) "
                + "values (?, ?, 'RAL1', 1.0, 100.00, true) returning id", Long.class, product, "V-" + uid());
        jdbcTemplate.update("insert into product_request_items (request_id, variant_id, qty, product_name_snapshot, sku_snapshot, price_snapshot) "
                + "values (?, ?, 1, 'P', 'SKU', 100.00)", id, variant);
        return id;
    }

    private ResponseEntity<String> post(String path) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.generateAccess(1L, "ADMIN", "a@x.test", "he"));
        return rest.exchange("/api/v1/admin/privacy" + path, HttpMethod.POST,
                new HttpEntity<>("{\"contact\":\"" + PHONE + "\"}", h), String.class);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
