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

/**
 * B-02: индексируемый адрес товара по slug + 301 со старого slug после переименования.
 */
class ProductSlugTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JwtService jwt;
    @Autowired
    private ObjectMapper json;

    @Test
    void card_opens_by_slug_generated_from_english_name() throws Exception {
        JsonNode created = createProduct("PAINT-1");
        long id = created.get("id").asLong();
        assertThat(created.get("slug").asText()).isEqualTo("paint");

        // по slug
        ResponseEntity<String> bySlug = rest.getForEntity("/api/v1/products/paint", String.class);
        assertThat(bySlug.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(bySlug.getBody()).get("slug").asText()).isEqualTo("paint");

        // по числовому id — остаётся для админки/внутренних вызовов
        ResponseEntity<String> byId = rest.getForEntity("/api/v1/products/" + id, String.class);
        assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(byId.getBody()).get("id").asLong()).isEqualTo(id);
    }

    @Test
    void renamed_slug_returns_301_to_the_new_address() throws Exception {
        JsonNode created = createProduct("PAINT-2");
        long id = created.get("id").asLong();
        long variantId = created.get("variants").get(0).get("id").asLong();

        // переименование slug через админский CRUD
        ResponseEntity<String> upd = admin(HttpMethod.PUT, "/api/v1/admin/products/" + id,
                updateBodyWithSlug("PAINT-2", "premium-paint", variantId));
        assertThat(upd.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(upd.getBody()).get("slug").asText()).isEqualTo("premium-paint");

        // старый slug → 301 на новый (TestRestTemplate не следует за редиректами)
        ResponseEntity<String> old = rest.getForEntity("/api/v1/products/paint", String.class);
        assertThat(old.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(old.getHeaders().getLocation()).hasToString("/api/v1/products/premium-paint");

        // новый slug открывается
        ResponseEntity<String> now = rest.getForEntity("/api/v1/products/premium-paint", String.class);
        assertThat(now.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknown_slug_is_404() {
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/products/does-not-exist", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private JsonNode createProduct(String sku) throws Exception {
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('cat-slug-" + sku + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('brand-slug-" + sku + "','B') returning id", Long.class);

        ObjectNode node = json.createObjectNode();
        node.put("category_id", categoryId);
        node.put("brand_id", brandId);
        node.put("sku", sku);
        node.put("paint_type", "INTERIOR");
        node.put("usage_area", "INDOOR");
        node.put("finish", "MATTE");
        node.put("base_type", "WATER");
        ObjectNode tr = node.putObject("translations");
        tr.putObject("he").put("name", "צבע");
        tr.putObject("en").put("name", "Paint");
        ArrayNode vs = node.putArray("variants");
        vs.add(variant("V-" + sku));

        ResponseEntity<String> resp = admin(HttpMethod.POST, "/api/v1/admin/products", node.toString());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(resp.getBody());
    }

    private String updateBodyWithSlug(String sku, String slug, long variantId) {
        ObjectNode node = json.createObjectNode();
        node.put("category_id", jdbcTemplate.queryForObject(
                "select category_id from products where sku = ?", Long.class, sku));
        node.put("brand_id", jdbcTemplate.queryForObject(
                "select brand_id from products where sku = ?", Long.class, sku));
        node.put("sku", sku);
        node.put("slug", slug);
        node.put("paint_type", "INTERIOR");
        node.put("usage_area", "INDOOR");
        node.put("finish", "MATTE");
        node.put("base_type", "WATER");
        ObjectNode tr = node.putObject("translations");
        tr.putObject("he").put("name", "צבע");
        tr.putObject("en").put("name", "Paint");
        ObjectNode v = variant("V-" + sku);
        v.put("id", variantId);
        node.putArray("variants").add(v);
        return node.toString();
    }

    private ObjectNode variant(String sku) {
        ObjectNode v = json.createObjectNode();
        v.put("sku", sku);
        v.put("color_code", "RAL1");
        v.put("color_hex", "#FFAA00");
        v.put("volume_liters", 1.0);
        v.put("price", new BigDecimal("100.00"));
        return v;
    }

    private ResponseEntity<String> admin(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@tzevabest.test", "he"));
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
