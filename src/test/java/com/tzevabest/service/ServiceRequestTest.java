package com.tzevabest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.media.service.MediaPresigner;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-11.1: публичный приём заявки на покраску — гостем и авторизованным, отказ без согласия, привязка
 * фото через presigned-загрузку в приватный бакет, номер из последовательности.
 */
class ServiceRequestTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;
    @Autowired
    private MediaPresigner presigner;

    @Test
    void guest_creates_request_with_sequence_number() throws Exception {
        String body = base("0501110001", true);
        ResponseEntity<String> resp = post(body, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode node = json.readTree(resp.getBody());
        assertThat(node.get("request_number").asText()).startsWith("SR-");
        assertThat(node.get("status").asText()).isEqualTo("new");

        // история и заявка сохранены; согласие записано
        Integer reqs = jdbcTemplate.queryForObject("select count(*) from service_requests", Integer.class);
        assertThat(reqs).isEqualTo(1);
        Integer hist = jdbcTemplate.queryForObject(
                "select count(*) from service_request_history where to_status = 'NEW'", Integer.class);
        assertThat(hist).isEqualTo(1);
        Integer consent = jdbcTemplate.queryForObject(
                "select count(*) from consents where source = 'service_request'", Integer.class);
        assertThat(consent).isEqualTo(1);
    }

    @Test
    void number_comes_from_sequence_and_is_unique() throws Exception {
        String n1 = json.readTree(post(base("0501110002", true), null).getBody()).get("request_number").asText();
        String n2 = json.readTree(post(base("0501110003", true), null).getBody()).get("request_number").asText();
        assertThat(n1).isNotEqualTo(n2);
        assertThat(n1).matches("SR-\\d{6}-\\d{4}");
    }

    @Test
    void authenticated_creation_links_user() throws Exception {
        long userId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, first_name, last_name, role, status)
                values (?, 'x', 'C', 'U', 'CUSTOMER', 'ACTIVE') returning id""",
                Long.class, "cust-" + uid() + "@x.test");
        String token = jwt.generateAccess(userId, "CUSTOMER", "c@x.test", "he");

        ResponseEntity<String> resp = post(base("0501110004", true), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long linked = jdbcTemplate.queryForObject(
                "select user_id from service_requests order by id desc limit 1", Long.class);
        assertThat(linked).isEqualTo(userId);
    }

    @Test
    void refuses_without_consent() throws Exception {
        ResponseEntity<String> resp = post(base("0501110005", false), null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(resp.getBody()).get("code").asText()).isEqualTo("service_request.consent_required");
    }

    @Test
    void photos_go_to_private_bucket_and_are_attached() throws Exception {
        // presigned-загрузка: URL указывает на ПРИВАТНЫЙ бакет и подписан
        String presignBody = "{\"files\":[{\"filename\":\"room.jpg\",\"content_type\":\"image/jpeg\"},"
                + "{\"filename\":\"wall.png\",\"content_type\":\"image/png\"}]}";
        ResponseEntity<String> presign = rest.exchange("/api/v1/service-requests/photos/presign",
                HttpMethod.POST, new HttpEntity<>(presignBody, jsonHeaders(null)), String.class);
        assertThat(presign.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode uploads = json.readTree(presign.getBody()).get("uploads");
        assertThat(uploads).hasSize(2);
        String key1 = uploads.get(0).get("object_key").asText();
        assertThat(uploads.get(0).get("upload_url").asText())
                .contains("tzevabest-private")       // приватный бакет
                .contains("X-Amz-Signature");         // presigned
        assertThat(key1).startsWith("service-requests/");
        String key2 = uploads.get(1).get("object_key").asText();

        // создаём заявку с этими ключами
        String body = "{\"customer_name\":\"Dana\",\"customer_phone\":\"0501110006\",\"property_type\":\"APARTMENT\","
                + "\"consent\":true,\"photo_keys\":[\"" + key1 + "\",\"" + key2 + "\"]}";
        ResponseEntity<String> resp = post(body, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(resp.getBody()).get("photo_count").asInt()).isEqualTo(2);

        // медиа хранит ключ объекта, публичного url нет
        var row = jdbcTemplate.queryForMap("select object_key, url from service_request_media limit 1");
        assertThat((String) row.get("object_key")).startsWith("service-requests/");
        assertThat(row.get("url")).isNull();

        // выдача — presigned GET из приватного бакета
        String download = presigner.presignDownload(key1);
        assertThat(download).contains("tzevabest-private").contains("X-Amz-Signature");
    }

    @Test
    void foreign_photo_key_is_rejected() throws Exception {
        String body = "{\"customer_name\":\"Dana\",\"customer_phone\":\"0501110007\",\"property_type\":\"APARTMENT\","
                + "\"consent\":true,\"photo_keys\":[\"evil/other-bucket-key.jpg\"]}";
        ResponseEntity<String> resp = post(body, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(resp.getBody()).get("code").asText()).isEqualTo("service_request.photo_key_invalid");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String base(String phone, boolean consent) {
        return "{\"customer_name\":\"Dana\",\"customer_phone\":\"" + phone + "\",\"customer_email\":\"dana@x.test\","
                + "\"property_type\":\"APARTMENT\",\"area_sqm\":80,\"rooms_count\":3,\"ceiling_height_m\":2.7,"
                + "\"work_types\":[\"WALLS\",\"CEILING\"],\"city\":\"Tel Aviv\",\"consent\":" + consent + "}";
    }

    private ResponseEntity<String> post(String body, String token) {
        return rest.exchange("/api/v1/service-requests", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(token)), String.class);
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
