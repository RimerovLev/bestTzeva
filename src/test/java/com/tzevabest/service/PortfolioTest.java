package com.tzevabest.service;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T-11.4: публичное портфолио (пары до/после, фильтры, скрытие черновиков) и создание из заявки. */
class PortfolioTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;
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
    void detail_returns_paired_before_after_and_hides_unpublished() throws Exception {
        long published = seedProject("villa-tlv", true, "APARTMENT", "Tel Aviv");
        jdbcTemplate.update("insert into portfolio_media (project_id, url, kind, sort_order, pair_group) values "
                + "(?, 'http://x/before.jpg', 'BEFORE', 1, 'living'), "
                + "(?, 'http://x/after.jpg', 'AFTER', 2, 'living'), "
                + "(?, 'http://x/process.jpg', 'PROCESS', 3, null)", published, published, published);
        long draft = seedProject("draft-1", false, "HOUSE", "Haifa");

        // детали: готовая пара + одиночное медиа
        JsonNode d = json.readTree(get("/api/v1/portfolio/villa-tlv").getBody());
        assertThat(d.get("pairs")).hasSize(1);
        assertThat(d.get("pairs").get(0).get("before_url").asText()).isEqualTo("http://x/before.jpg");
        assertThat(d.get("pairs").get(0).get("after_url").asText()).isEqualTo("http://x/after.jpg");
        assertThat(d.get("standalone")).hasSize(1);
        assertThat(d.get("standalone").get(0).get("kind").asText()).isEqualTo("PROCESS");

        // черновик скрыт от публичного API
        assertThat(get("/api/v1/portfolio/draft-1").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void list_filters_and_excludes_unpublished() throws Exception {
        seedProject("apt-tlv", true, "APARTMENT", "Tel Aviv");
        seedProject("house-haifa", true, "HOUSE", "Haifa");
        seedProject("hidden", false, "APARTMENT", "Tel Aviv");

        assertThat(json.readTree(get("/api/v1/portfolio").getBody()).get("total_elements").asInt()).isEqualTo(2);
        assertThat(json.readTree(get("/api/v1/portfolio?property_type=APARTMENT").getBody())
                .get("total_elements").asInt()).isEqualTo(1); // только опубликованная квартира
        assertThat(json.readTree(get("/api/v1/portfolio?city=Haifa").getBody())
                .get("total_elements").asInt()).isEqualTo(1);
    }

    @Test
    void admin_uploads_media_to_public_bucket() throws Exception {
        long id = json.readTree(createProject("proj-" + uid()).getBody()).get("id").asLong();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(managerToken());
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource("img".getBytes()) {
            @Override
            public String getFilename() {
                return "after.png";
            }
        });
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/admin/portfolio/" + id + "/media?kind=AFTER&pair_group=living",
                HttpMethod.POST, new HttpEntity<>(parts, h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(resp.getBody()).get("url").asText()).contains("test-media");
        Integer media = jdbcTemplate.queryForObject(
                "select count(*) from portfolio_media where project_id = ?", Integer.class, id);
        assertThat(media).isEqualTo(1);
    }

    @Test
    void create_from_request_pulls_fields_without_transferring_private_photos() throws Exception {
        // завершённая заявка с видами работ и приватным фото
        long reqId = jdbcTemplate.queryForObject("""
                insert into service_requests (request_number, customer_name, customer_phone, property_type,
                        area_sqm, city, source, consent_given_at, status, locale)
                values (?, 'Dana', '+972500000000', 'HOUSE', 120, 'Eilat', 'SITE_FORM', now(), 'COMPLETED', 'he')
                returning id""", Long.class, "SR-" + uid());
        jdbcTemplate.update("insert into service_request_work_types (request_id, work_type) values (?, 'WALLS'), (?, 'FACADE')", reqId, reqId);
        jdbcTemplate.update("insert into service_request_media (request_id, object_key, type) values (?, 'service-requests/private.jpg', 'IMAGE')", reqId);

        ResponseEntity<String> resp = rest.exchange("/api/v1/admin/portfolio/from-request/" + reqId,
                HttpMethod.POST, new HttpEntity<>(managerHeaders()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long projectId = json.readTree(resp.getBody()).get("id").asLong();

        // виды работ перенесены
        Integer wt = jdbcTemplate.queryForObject(
                "select count(*) from portfolio_project_work_types where project_id = ?", Integer.class, projectId);
        assertThat(wt).isEqualTo(2);
        // фото НЕ перенесены
        Integer media = jdbcTemplate.queryForObject(
                "select count(*) from portfolio_media where project_id = ?", Integer.class, projectId);
        assertThat(media).isZero();
        // приватное фото заявки на месте
        Integer priv = jdbcTemplate.queryForObject(
                "select count(*) from service_request_media where request_id = ?", Integer.class, reqId);
        assertThat(priv).isEqualTo(1);
        // проект не опубликован
        Boolean published = jdbcTemplate.queryForObject(
                "select is_published from portfolio_projects where id = ?", Boolean.class, projectId);
        assertThat(published).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long seedProject(String slug, boolean published, String propertyType, String city) {
        long id = jdbcTemplate.queryForObject("""
                insert into portfolio_projects (slug, property_type, city, is_published)
                values (?, ?, ?, ?) returning id""", Long.class, slug, propertyType, city, published);
        jdbcTemplate.update("insert into portfolio_translations (project_id, locale, title) values (?, 'he', ?)",
                id, "פרויקט " + slug);
        return id;
    }

    private ResponseEntity<String> createProject(String slug) {
        String body = "{\"slug\":\"" + slug + "\",\"property_type\":\"APARTMENT\",\"city\":\"Tel Aviv\","
                + "\"translations\":[{\"locale\":\"he\",\"title\":\"דירה\"}]}";
        HttpHeaders h = managerHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/admin/portfolio", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> get(String path) {
        return rest.getForEntity(path, String.class);
    }

    private HttpHeaders managerHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(managerToken());
        return h;
    }

    private String managerToken() {
        return jwt.generateAccess(1L, "MANAGER", "m@x.test", "he");
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
