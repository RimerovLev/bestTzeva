package com.tzevabest.catalog;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-48: поиск по имени должен опираться на триграммный GIN-индекс по нормализованной колонке,
 * а не сканировать product_translations целиком. Проверяем план на достаточном объёме данных.
 */
class ProductSearchIndexTest extends IntegrationTest {

    @Test
    void name_search_uses_normalized_trigram_index() {
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('idx-cat') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('idx-brand','B') returning id", Long.class);

        // 20 000 товаров с переводами — планировщик выберет индекс только при заметном объёме.
        jdbcTemplate.update("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                select ?, ?, 'IDX-' || g, 'INTERIOR','INDOOR','MATTE','WATER', true
                from generate_series(1, 20000) g""", categoryId, brandId);
        // Общие имена + один редкий токен: триграммы 'zqxwv-...' селективны, план обязан взять индекс.
        jdbcTemplate.update("""
                insert into product_translations (product_id, locale, name)
                select id, 'he', 'Paint ' || id from products where sku like 'IDX-%'""");
        jdbcTemplate.update("""
                update product_translations set name = 'Zqxwvu Rare Marker'
                where product_id = (select id from products where sku = 'IDX-9999')""");
        jdbcTemplate.execute("analyze product_translations");

        List<String> plan = jdbcTemplate.queryForList(
                "explain (costs off) select 1 from product_translations "
                        + "where name_normalized ilike immutable_unaccent('%zqxwvu%')",
                String.class);
        String planText = String.join("\n", plan);

        assertThat(planText).contains("ix_product_translations_name_norm_trgm");
        assertThat(planText).doesNotContain("Seq Scan on product_translations");
    }
}
