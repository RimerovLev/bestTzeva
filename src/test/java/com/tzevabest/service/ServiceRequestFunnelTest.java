package com.tzevabest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T-11.3: генерация PDF котировки на иврите, отложенное напоминание о замере, список/назначение. */
class ServiceRequestFunnelTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;
    @Autowired
    private S3Client s3;

    private long managerId;

    @BeforeEach
    void setup() {
        managerId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, first_name, last_name, role, status)
                values (?, 'x', 'M', 'G', 'MANAGER', 'ACTIVE') returning id""",
                Long.class, "mgr-" + uid() + "@x.test");
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket("tzevabest-private").build());
        } catch (SdkException exists) {
            // уже создан
        }
    }

    @Test
    void quote_generates_hebrew_pdf_and_notifies_client() throws Exception {
        long id = seed("MEASURED", "dana@x.test");
        ResponseEntity<String> resp = post(id + "/quote",
                "{\"price\":5400,\"work_composition\":\"צביעת קירות\",\"term_days\":7,\"comment\":\"כולל חומרים\"}");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(resp.getBody()).get("status").asText()).isEqualTo("quoted");

        // PDF лежит в приватном бакете и валиден (сигнатура %PDF)
        String key = jdbcTemplate.queryForObject(
                "select quote_pdf_key from service_requests where id = ?", String.class, id);
        assertThat(key).startsWith("quotes/" + id);
        byte[] pdf = s3.getObject(GetObjectRequest.builder().bucket("tzevabest-private").key(key).build())
                .readAllBytes();
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // клиенту поставлено уведомление о котировке
        Integer notif = jdbcTemplate.queryForObject(
                "select count(*) from notifications_log where template_code = 'service_request_quoted'", Integer.class);
        assertThat(notif).isEqualTo(1);

        // presigned-ссылка на PDF для персонала
        String url = json.readTree(post0("GET", id + "/quote-pdf", null).getBody()).get("url").asText();
        assertThat(url).contains("tzevabest-private").contains("X-Amz-Signature");
    }

    @Test
    void measurement_schedules_reminder_24h_before() throws Exception {
        long id = seed("CONTACTED", "dana@x.test");
        Instant at = Instant.now().plus(3, ChronoUnit.DAYS);
        ResponseEntity<String> resp = post(id + "/measurement", "{\"at\":\"" + at + "\"}");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(resp.getBody()).get("status").asText()).isEqualTo("measurement_scheduled");

        var row = jdbcTemplate.queryForMap(
                "select next_attempt_at from notifications_log where template_code = 'measurement_reminder'");
        Instant reminderAt = ((java.sql.Timestamp) row.get("next_attempt_at")).toInstant();
        Instant expected = at.minus(1, ChronoUnit.DAYS);
        // напоминание за сутки до замера (с точностью до минуты)
        assertThat(Math.abs(reminderAt.getEpochSecond() - expected.getEpochSecond())).isLessThan(60);
    }

    @Test
    void list_filters_and_assign_and_comment() throws Exception {
        long id = seed("NEW", "dana@x.test");
        // список с фильтром по статусу
        ResponseEntity<String> listResp = rest.exchange("/api/v1/admin/service-requests?status=NEW",
                HttpMethod.GET, new HttpEntity<>(headers()), String.class);
        JsonNode page = json.readTree(listResp.getBody());
        assertThat(page.get("total_elements").asInt()).isEqualTo(1);

        // назначение менеджера
        ResponseEntity<String> assigned = rest.exchange("/api/v1/admin/service-requests/" + id + "/assignee",
                HttpMethod.PATCH, new HttpEntity<>("{\"manager_id\":" + managerId + "}", headers()), String.class);
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(assigned.getBody()).get("assigned_manager_id").asLong()).isEqualTo(managerId);

        // внутренний комментарий
        assertThat(post(id + "/comments", "{\"text\":\"позвонить утром\"}").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        Integer comments = jdbcTemplate.queryForObject(
                "select count(*) from service_request_comments where request_id = ?", Integer.class, id);
        assertThat(comments).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long seed(String status, String email) {
        return jdbcTemplate.queryForObject("""
                insert into service_requests (request_number, customer_name, customer_phone, customer_email,
                        property_type, source, consent_given_at, status, locale)
                values (?, 'Dana', '+972500000000', ?, 'APARTMENT', 'SITE_FORM', now(), ?, 'he') returning id""",
                Long.class, "SR-" + uid(), email, status);
    }

    private ResponseEntity<String> post(String path, String body) {
        return rest.exchange("/api/v1/admin/service-requests/" + path, HttpMethod.POST,
                new HttpEntity<>(body, headers()), String.class);
    }

    private ResponseEntity<String> post0(String method, String path, String body) {
        return rest.exchange("/api/v1/admin/service-requests/" + path, HttpMethod.valueOf(method),
                new HttpEntity<>(body, headers()), String.class);
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.generateAccess(managerId, "MANAGER", "m@x.test", "he"));
        return h;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 10);
    }
}
