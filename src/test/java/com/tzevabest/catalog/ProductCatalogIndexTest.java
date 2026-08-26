package com.tzevabest.catalog;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-31: после удаления избыточного ix_products_active_paint_type (V25) листинг каталога по-прежнему
 * идёт через частичный ix_products_catalog_list, а не Seq Scan — план не ухудшился.
 */
class ProductCatalogIndexTest extends IntegrationTest {

    private static final int PRODUCTS = 20_000;
    private static final int CATEGORIES = 50;

    @Test
    void catalog_list_query_uses_partial_index_after_dropping_redundant_one() {
        jdbcTemplate.update("insert into categories (slug) select 'c-' || g from generate_series(1, ?) g", CATEGORIES);
        Long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b-idx', 'B') returning id", Long.class);
        Long firstCat = jdbcTemplate.queryForObject("select min(id) from categories", Long.class);

        jdbcTemplate.update("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                select ? + (g % ?),
                       ?,
                       'SKU-IDX-' || g,
                       (array['INTERIOR','EXTERIOR','WOOD','METAL'])[1 + (g % 4)],
                       'INDOOR', 'MATTE', 'WATER', true
                from generate_series(1, ?) g
                """, firstCat, CATEGORIES, brandId, PRODUCTS);
        jdbcTemplate.execute("analyze products");

        List<String> plan = jdbcTemplate.queryForList(
                "explain (analyze, costs off) "
                        + "select id from products where is_active and category_id = " + firstCat
                        + " and paint_type = 'INTERIOR' order by created_at desc limit 20",
                String.class);
        String text = String.join("\n", plan);
        System.out.println("[R-31] catalog list plan:\n" + text);

        assertThat(text).doesNotContain("Seq Scan on products");
        assertThat(text.toLowerCase()).contains("ix_products_catalog_list");
    }
}
