package com.tzevabest.catalog;

import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.service.SkuService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** T-4.2.2: уникальность SKU при коллизии и конкурентном создании. */
class SkuServiceTest extends IntegrationTest {

    @Autowired
    private SkuService skuService;

    @Test
    void collision_gets_numeric_suffix() {
        long product = seedProduct();
        String base = "TMB-INT-RAL9010-05L";
        insertVariant(product, base);
        String next = skuService.uniqueSku("TMB", PaintType.INTERIOR, "RAL9010", new BigDecimal("0.5"));
        assertThat(next).isEqualTo(base + "-2");
    }

    @Test
    void concurrent_creation_yields_distinct_skus() throws Exception {
        // Одинаковые исходные данные (brand/type/color/volume) на РАЗНЫХ товарах дают один базовый SKU;
        // уникальный индекс по sku разводит их (base и base-2). (Один цвет+объём на одном товаре
        // запрещён отдельным индексом, поэтому берём два товара.)
        long p1 = seedProduct();
        long p2 = seedProduct();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> results = pool.invokeAll(List.of(
                () -> createWithRetry(p1), () -> createWithRetry(p2)));
        String a = results.get(0).get();
        String b = results.get(1).get();
        pool.shutdown();

        assertThat(a).isNotEqualTo(b); // разные SKU при одинаковых исходных данных
        Integer variants = jdbcTemplate.queryForObject(
                "select count(*) from product_variants where product_id in (?, ?)", Integer.class, p1, p2);
        assertThat(variants).isEqualTo(2);
    }

    /** Подбирает SKU и вставляет; при гонке (уникальный индекс) повторяет с новым суффиксом. */
    private String createWithRetry(long product) {
        for (int attempt = 0; attempt < 50; attempt++) {
            String sku = skuService.uniqueSku("TMB", PaintType.INTERIOR, "RAL9010", new BigDecimal("0.5"));
            try {
                insertVariant(product, sku);
                return sku;
            } catch (DataIntegrityViolationException race) {
                // конкурент занял этот SKU (уникальный индекс) — повторяем
            }
        }
        throw new IllegalStateException("could not allocate sku");
    }

    private long seedProduct() {
        String t = UUID.randomUUID().toString().substring(0, 8);
        long cat = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brand = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        return jdbcTemplate.queryForObject("insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active) "
                + "values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id", Long.class, cat, brand, "P-" + t);
    }

    private void insertVariant(long product, String sku) {
        jdbcTemplate.update("insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active) "
                + "values (?, ?, 'RAL9010', 0.5, 100.00, true)", product, sku);
    }
}
