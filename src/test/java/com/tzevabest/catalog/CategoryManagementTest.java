package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Управление категориями (T-4.1): построение дерева с подсчётами, цикл, удаление непустой категории,
 * подстановка HE при отсутствующем переводе, ограничение глубины.
 */
class CategoryManagementTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JwtService jwt;

    @Autowired
    private ObjectMapper json;

    // ── Дерево и подсчёты ────────────────────────────────────────────────────────

    @Test
    void builds_tree_with_children_and_nested_product_counts() throws Exception {
        long root = create(null, "צבעים", "Paints");
        long child = create(root, "פנים", "Interior");

        long brand = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b','B') returning id", Long.class);
        insertActiveProduct(brand, root, "SKU-ROOT-1");
        insertActiveProduct(brand, child, "SKU-CHILD-1");
        insertActiveProduct(brand, child, "SKU-CHILD-2");

        JsonNode tree = getJson("/api/v1/categories?tree=true");
        assertThat(tree.isArray()).isTrue();
        JsonNode rootNode = tree.get(0);
        assertThat(rootNode.get("id").asLong()).isEqualTo(root);
        assertThat(rootNode.get("children")).hasSize(1);
        assertThat(rootNode.get("children").get(0).get("id").asLong()).isEqualTo(child);
        // корень: 1 свой + 2 у ребёнка = 3 (с учётом вложенных); ребёнок: 2
        assertThat(rootNode.get("product_count").asLong()).isEqualTo(3);
        assertThat(rootNode.get("children").get(0).get("product_count").asLong()).isEqualTo(2);

        // плоский список: обе категории без вложенности (поле children отсутствует)
        JsonNode flat = getJson("/api/v1/categories");
        assertThat(flat).hasSize(2);
        assertThat(flat.get(0).path("children").isMissingNode()).isTrue();
    }

    // ── Цикл ─────────────────────────────────────────────────────────────────────

    @Test
    void moving_category_under_its_descendant_is_rejected_as_cycle() throws Exception {
        long a = create(null, "A", "A");
        long b = create(a, "B", "B");

        // переносим A под B (его потомка) → цикл
        ResponseEntity<String> resp = admin(HttpMethod.PUT, "/api/v1/admin/categories/" + a,
                body(null, b, "A", "A"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(resp)).isEqualTo("catalog.category.cycle");
    }

    // ── Удаление непустой ────────────────────────────────────────────────────────

    @Test
    void deleting_category_with_subcategory_or_product_is_rejected() throws Exception {
        long parent = create(null, "P", "Parent");
        create(parent, "C", "Child");

        ResponseEntity<String> withSub = admin(HttpMethod.DELETE, "/api/v1/admin/categories/" + parent, null);
        assertThat(withSub.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(withSub)).isEqualTo("catalog.category.not_empty");

        // отдельная категория с товаром — тоже нельзя
        long withProduct = create(null, "WP", "WithProduct");
        long brand = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b2','B2') returning id", Long.class);
        insertActiveProduct(brand, withProduct, "SKU-WP-1");
        ResponseEntity<String> resp = admin(HttpMethod.DELETE, "/api/v1/admin/categories/" + withProduct, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(resp)).isEqualTo("catalog.category.not_empty");
    }

    // ── Подстановка HE ───────────────────────────────────────────────────────────

    @Test
    void missing_translation_falls_back_to_he() throws Exception {
        create(null, "צבע עברית", null); // только HE, без EN/RU

        JsonNode ru = getJson("/api/v1/categories?locale=ru");
        assertThat(ru.get(0).get("name").asText()).isEqualTo("צבע עברית");
        JsonNode he = getJson("/api/v1/categories?locale=he");
        assertThat(he.get(0).get("name").asText()).isEqualTo("צבע עברית");
    }

    // ── Глубина ──────────────────────────────────────────────────────────────────

    @Test
    void fourth_level_category_is_rejected() throws Exception {
        long l1 = create(null, "L1", "L1");
        long l2 = create(l1, "L2", "L2");
        long l3 = create(l2, "L3", "L3");

        ResponseEntity<String> resp = admin(HttpMethod.POST, "/api/v1/admin/categories", body(null, l3, "L4", "L4"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(resp)).isEqualTo("catalog.category.max_depth");
    }

    // ── Slug ─────────────────────────────────────────────────────────────────────

    @Test
    void slug_is_generated_from_english_name_with_suffix_on_collision() throws Exception {
        ResponseEntity<String> first = admin(HttpMethod.POST, "/api/v1/admin/categories", body(null, null, "צבע", "Paints"));
        ResponseEntity<String> second = admin(HttpMethod.POST, "/api/v1/admin/categories", body(null, null, "צבע2", "Paints"));

        assertThat(json.readTree(first.getBody()).get("slug").asText()).isEqualTo("paints");
        assertThat(json.readTree(second.getBody()).get("slug").asText()).isEqualTo("paints-2");
    }

    // ── Reorder ──────────────────────────────────────────────────────────────────

    @Test
    void reorder_changes_parent_and_sort_order() throws Exception {
        long a = create(null, "A", "A");
        long b = create(null, "B", "B");

        // делаем B ребёнком A
        String reorder = "{\"items\":[{\"id\":" + b + ",\"parent_id\":" + a + ",\"sort_order\":1}]}";
        ResponseEntity<String> resp = admin(HttpMethod.PUT, "/api/v1/admin/categories/reorder", reorder);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode tree = getJson("/api/v1/categories?tree=true");
        assertThat(tree).hasSize(1); // только корень A
        assertThat(tree.get(0).get("id").asLong()).isEqualTo(a);
        assertThat(tree.get(0).get("children").get(0).get("id").asLong()).isEqualTo(b);
    }

    @Test
    void reorder_creating_a_cycle_is_rejected() throws Exception {
        long a = create(null, "A", "A");
        long b = create(a, "B", "B");

        // A под B и B под A одновременно — цикл
        String reorder = "{\"items\":["
                + "{\"id\":" + a + ",\"parent_id\":" + b + ",\"sort_order\":0},"
                + "{\"id\":" + b + ",\"parent_id\":" + a + ",\"sort_order\":0}]}";
        ResponseEntity<String> resp = admin(HttpMethod.PUT, "/api/v1/admin/categories/reorder", reorder);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(resp)).isEqualTo("catalog.category.cycle");
    }

    // ── Кэш ──────────────────────────────────────────────────────────────────────

    @Test
    void tree_cache_is_invalidated_on_change() throws Exception {
        create(null, "First", "First");
        assertThat(getJson("/api/v1/categories?tree=true")).hasSize(1); // прогреваем кэш

        create(null, "Second", "Second"); // мутация должна сбросить кэш
        assertThat(getJson("/api/v1/categories?tree=true")).hasSize(2);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long create(Long parentId, String heName, String enName) throws Exception {
        ResponseEntity<String> resp = admin(HttpMethod.POST, "/api/v1/admin/categories",
                body(null, parentId, heName, enName));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(resp.getBody()).get("id").asLong();
    }

    private String body(String slug, Long parentId, String heName, String enName) {
        var node = json.createObjectNode();
        if (slug != null) {
            node.put("slug", slug);
        }
        if (parentId != null) {
            node.put("parent_id", parentId);
        }
        var translations = node.putObject("translations");
        translations.putObject("he").put("name", heName);
        if (enName != null) {
            translations.putObject("en").put("name", enName);
        }
        return node.toString();
    }

    private ResponseEntity<String> admin(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@tzevabest.test", "he"));
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode getJson(String path) throws Exception {
        ResponseEntity<String> resp = rest.getForEntity(path, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resp.getBody());
    }

    private String code(ResponseEntity<String> resp) throws Exception {
        return json.readTree(resp.getBody()).get("code").asText();
    }

    private void insertActiveProduct(long brandId, long categoryId, String sku) {
        jdbcTemplate.update("""
                        insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                        values (?, ?, ?, 'INTERIOR', 'INDOOR', 'MATTE', 'WATER', true)""",
                categoryId, brandId, sku);
    }
}
