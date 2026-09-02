package com.tzevabest.request;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T-14.3: покрытие админ-API заявок на товар — список, метрики, карточка, назначение, комментарии. */
class ProductRequestAdminExtraTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;

    private long managerId;
    private long requestId;

    @BeforeEach
    void seed() {
        managerId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, first_name, last_name, role, status)
                values (?, 'x', 'M', 'G', 'MANAGER', 'ACTIVE') returning id""",
                Long.class, "mgr-" + uid() + "@x.test");
        requestId = jdbcTemplate.queryForObject("""
                insert into product_requests (request_number, customer_name, customer_phone, source,
                        consent_given_at, policy_version, status)
                values (?, 'Dana', '+972500000000', 'SITE_FORM', now(), '2026-08-01', 'NEW') returning id""",
                Long.class, "PR-" + uid());
    }

    @Test
    void list_metrics_detail() throws Exception {
        JsonNode list = json.readTree(get("").getBody());
        assertThat(list.get("total_elements").asInt()).isGreaterThanOrEqualTo(1);

        // фильтр по статусу
        assertThat(json.readTree(get("?status=NEW").getBody()).get("total_elements").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode metrics = json.readTree(get("/metrics").getBody());
        assertThat(metrics).isNotNull();

        JsonNode detail = json.readTree(get("/" + requestId).getBody());
        assertThat(detail.get("request_number").asText()).startsWith("PR-");
    }

    @Test
    void assign_and_comment() throws Exception {
        ResponseEntity<String> assigned = rest.exchange("/api/v1/admin/product-requests/" + requestId + "/assignee",
                HttpMethod.PATCH, new HttpEntity<>("{\"manager_id\":" + managerId + "}", jsonHeaders()), String.class);
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long assignedTo = jdbcTemplate.queryForObject(
                "select assigned_manager_id from product_requests where id = ?", Long.class, requestId);
        assertThat(assignedTo).isEqualTo(managerId);

        ResponseEntity<String> comment = rest.exchange("/api/v1/admin/product-requests/" + requestId + "/comments",
                HttpMethod.POST, new HttpEntity<>("{\"text\":\"перезвонить утром\"}", jsonHeaders()), String.class);
        assertThat(comment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Integer comments = jdbcTemplate.queryForObject(
                "select count(*) from product_request_comments where request_id = ?", Integer.class, requestId);
        assertThat(comments).isEqualTo(1);
    }

    private ResponseEntity<String> get(String suffix) {
        return rest.exchange("/api/v1/admin/product-requests" + suffix, HttpMethod.GET,
                new HttpEntity<>(headers()), String.class);
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.generateAccess(managerId, "MANAGER", "m@x.test", "he"));
        return h;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = headers();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
