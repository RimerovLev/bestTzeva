package com.tzevabest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-11.1.1: общая защита формы покраски — honeypot молча (201, нет записи), слишком быстрое
 * заполнение по токену, блок-лист, единый бюджет лимита для форматов одного номера. Клиент без
 * авто-ретраев (429 c Retry-After не должен усыплять HttpClient5).
 */
class ServiceSpamTest extends IntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;

    private RestTemplate rest;

    @BeforeEach
    void initClient() {
        var factory = new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableAutomaticRetries().build());
        rest = new RestTemplate(factory);
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
    }

    @Test
    void honeypot_is_dropped_silently_with_201_and_logged() throws Exception {
        String body = base("0502220001", true) + hp("http://spam.example", null);
        ResponseEntity<String> resp = post(body, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(json.readTree(resp.getBody()).get("request_number").asText()).startsWith("SR-");
        assertThat(count("service_requests")).isZero();               // заявка не создана
        assertThat(drops("honeypot")).isEqualTo(1);                   // отсечение в общем журнале
    }

    @Test
    void too_fast_submission_is_dropped() throws Exception {
        String token = json.readTree(get("/api/v1/service-requests/form-token").getBody())
                .get("form_token").asText();
        // отправляем немедленно — быстрее 3 секунд
        String body = base("0502220002", true) + hp(null, token);
        assertThat(post(body, null).getStatusCode().value()).isEqualTo(201);
        assertThat(count("service_requests")).isZero();
        assertThat(drops("too_fast")).isEqualTo(1);
    }

    @Test
    void blocklisted_phone_is_dropped() throws Exception {
        jdbcTemplate.update("insert into block_list (kind, value) values ('PHONE', '+972502220003')");
        String body = base("0502220003", true); // нормализуется в заблокированный номер
        assertThat(post(body, null).getStatusCode().value()).isEqualTo(201);
        assertThat(count("service_requests")).isZero();
        assertThat(drops("blocked")).isEqualTo(1);
    }

    @Test
    void phone_rate_limit_shares_budget_across_formats() {
        String[] phones = {"0502220009", "050-222-0009", "+972502220009", "972-50-222-0009"};
        int[] got = new int[4];
        for (int i = 0; i < 4; i++) {
            got[i] = post(base(phones[i], true), null).getStatusCode().value();
        }
        assertThat(got[0]).isEqualTo(201);
        assertThat(got[1]).isEqualTo(201);
        assertThat(got[2]).isEqualTo(201);
        assertThat(got[3]).isEqualTo(429); // четвёртый по тому же номеру
    }

    @Test
    void admin_can_add_to_block_list() {
        long managerId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, first_name, last_name, role, status)
                values ('mgr-spam@x.test', 'x', 'M', 'G', 'MANAGER', 'ACTIVE') returning id""", Long.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.generateAccess(managerId, "MANAGER", "mgr@x.test", "he"));
        ResponseEntity<String> resp = rest.exchange("http://localhost:" + port + "/api/v1/admin/spam/block-list",
                HttpMethod.POST, new HttpEntity<>("{\"kind\":\"IP\",\"value\":\"9.9.9.9\",\"reason\":\"abuse\"}", h),
                String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(count("block_list")).isEqualTo(1);
    }

    @Test
    void block_list_phone_is_stored_normalized_and_blocks_any_format() throws Exception {
        long managerId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, first_name, last_name, role, status)
                values ('mgr-spam2@x.test', 'x', 'M', 'G', 'MANAGER', 'ACTIVE') returning id""", Long.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.generateAccess(managerId, "MANAGER", "mgr2@x.test", "he"));
        String url = "http://localhost:" + port + "/api/v1/admin/spam/block-list";

        ResponseEntity<String> added = rest.exchange(url, HttpMethod.POST,
                new HttpEntity<>("{\"kind\":\"PHONE\",\"value\":\"050-222-0004\"}", h), String.class);
        assertThat(added.getStatusCode().value()).isEqualTo(201);
        assertThat(json.readTree(added.getBody()).get("value").asText()).isEqualTo("+972502220004");

        // тот же номер в другой записи — дубликат, мусор вместо номера — 422 (common.phone.invalid)
        assertThat(rest.exchange(url, HttpMethod.POST, new HttpEntity<>(
                "{\"kind\":\"PHONE\",\"value\":\"+972 50 222 0004\"}", h), String.class).getStatusCode().value())
                .isEqualTo(409);
        assertThat(rest.exchange(url, HttpMethod.POST, new HttpEntity<>(
                "{\"kind\":\"PHONE\",\"value\":\"12345\"}", h), String.class).getStatusCode().value())
                .isEqualTo(422);

        assertThat(post(base("0502220004", true), null).getStatusCode().value()).isEqualTo(201);
        assertThat(count("service_requests")).isZero();
        assertThat(drops("blocked")).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** Внутренние поля JSON (без фигурных скобок). */
    private String base(String phone, boolean consent) {
        return "\"customer_name\":\"Dana\",\"customer_phone\":\"" + phone + "\","
                + "\"property_type\":\"APARTMENT\",\"consent\":" + consent;
    }

    private String hp(String website, String formToken) {
        StringBuilder sb = new StringBuilder();
        if (website != null) {
            sb.append(",\"website\":\"").append(website).append("\"");
        }
        if (formToken != null) {
            sb.append(",\"form_token\":\"").append(formToken).append("\"");
        }
        return sb.toString();
    }

    private ResponseEntity<String> post(String innerJson, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return rest.exchange("http://localhost:" + port + "/api/v1/service-requests", HttpMethod.POST,
                new HttpEntity<>("{" + innerJson + "}", h), String.class);
    }

    private ResponseEntity<String> get(String path) {
        return rest.exchange("http://localhost:" + port + path, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private int drops(String reason) {
        return jdbcTemplate.queryForObject(
                "select count(*) from spam_drops where form = 'service_request' and reason = ?",
                Integer.class, reason);
    }
}
