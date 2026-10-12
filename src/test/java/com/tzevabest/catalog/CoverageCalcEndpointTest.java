package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tzevabest.iam.service.JwtService;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** B-04: публичный расчёт расхода на карточке — endpoint, авторизация (публичный POST), тело. */
class CoverageCalcEndpointTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JwtService jwt;
    @Autowired
    private ObjectMapper json;

    @Test
    void computes_coverage_for_product_with_coverage() throws Exception {
        createProduct("COV-1", new BigDecimal("10"), "2.5"); // расход 10 м²/л, банка 2.5 л

        ResponseEntity<String> resp = rest.postForEntity("/api/v1/products/paint/coverage-calc",
                jsonBody("{\"area\":20,\"coats\":2}"), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(resp.getBody());
        assertThat(body.get("calculable").asBoolean()).isTrue();
        JsonNode v = body.get("variants").get(0);
        assertThat(v.get("cans").asInt()).isEqualTo(2);              // ceil(40/10 / 2.5)
        assertThat(v.get("leftover_liters").asDouble()).isEqualTo(1.0);
    }

    @Test
    void product_without_coverage_is_not_calculable() throws Exception {
        createProduct("COV-2", null, "2.5");

        ResponseEntity<String> resp = rest.postForEntity("/api/v1/products/paint/coverage-calc",
                jsonBody("{\"area\":20}"), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(resp.getBody());
        assertThat(body.get("calculable").asBoolean()).isFalse();
        assertThat(body.get("variants")).isEmpty();
    }

    @Test
    void unknown_slug_is_404() {
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/products/nope/coverage-calc",
                jsonBody("{\"area\":20}"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void missing_area_is_rejected() throws Exception {
        createProduct("COV-3", new BigDecimal("10"), "2.5");
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/products/paint/coverage-calc",
                jsonBody("{\"coats\":2}"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void createProduct(String sku, BigDecimal coverage, String volume) {
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('cov-cat-" + sku + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('cov-brand-" + sku + "','B') returning id", Long.class);

        ObjectNode node = json.createObjectNode();
        node.put("category_id", categoryId);
        node.put("brand_id", brandId);
        node.put("sku", sku);
        node.put("paint_type", "INTERIOR");
        node.put("usage_area", "INDOOR");
        node.put("finish", "MATTE");
        node.put("base_type", "WATER");
        if (coverage != null) {
            node.put("coverage_sqm_per_liter", coverage);
        }
        ObjectNode tr = node.putObject("translations");
        tr.putObject("he").put("name", "צבע");
        tr.putObject("en").put("name", "Paint");
        ArrayNode vs = node.putArray("variants");
        ObjectNode v = json.createObjectNode();
        v.put("sku", "V-" + sku);
        v.put("color_code", "RAL1");
        v.put("color_hex", "#FFAA00");
        v.put("volume_liters", new BigDecimal(volume));
        v.put("price", new BigDecimal("100.00"));
        vs.add(v);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@tzevabest.test", "he"));
        ResponseEntity<String> resp = rest.exchange("/api/v1/admin/products", HttpMethod.POST,
                new HttpEntity<>(node.toString(), headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private HttpEntity<String> jsonBody(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
