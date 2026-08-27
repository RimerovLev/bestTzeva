package com.tzevabest.common.dev;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Генератор нагруженного dev-датасета. Только профиль {@code local} и под флагом
 * {@code app.dev.load-data=true}. Масштаб задаётся {@code app.dev.load-data-scale}:
 * <ul>
 *   <li>{@code small} (по умолчанию) — 500 товаров / 2500 вариантов / 1000 заказов /
 *       300 заявок — для повседневной разработки;</li>
 *   <li>{@code large} — 5000 / 30000 / 100000 заказов / 300k позиций / 20000 заявок —
 *       ~два года работы магазина, для замера планов запросов.</li>
 * </ul>
 * Set-based SQL (generate_series) в одной транзакции; в конце — явный {@code ANALYZE},
 * чтобы EXPLAIN работал на реальной статистике. Идемпотентен по наличию данных {@code LOAD-%}.
 * Счётчики подставляются через {@code {TOKEN}}-замены (без {@code String.format}, чтобы
 * literal {@code %} в LIKE/модуло SQL не требовали экранирования).
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.dev", name = "load-data", havingValue = "true")
@RequiredArgsConstructor
public class LoadDataGenerator implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager txManager;
    private final Environment environment;

    private record Scale(String name, int products, int variantsPer, int requests) {
        static final Scale SMALL = new Scale("small", 500, 5, 300);
        static final Scale LARGE = new Scale("large", 5000, 6, 20_000);
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject(
                "select count(*) from products where sku like 'LOAD-%'", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Load dataset already present ({} products) — skipping generation", existing);
            return;
        }

        Scale scale = "large".equalsIgnoreCase(environment.getProperty("app.dev.load-data-scale", "small"))
                ? Scale.LARGE : Scale.SMALL;

        log.info("Generating '{}' load dev dataset ({} products / {} variants / {} requests)...",
                scale.name(), scale.products(), scale.products() * scale.variantsPer(), scale.requests());
        long started = System.currentTimeMillis();

        new TransactionTemplate(txManager)
                .executeWithoutResult(status -> statements(scale).forEach(jdbc::execute));

        // Явный ANALYZE — иначе планировщик работает на дефолтных оценках и EXPLAIN ненадёжен.
        jdbc.execute("ANALYZE");

        log.info("Load '{}' dataset generated + ANALYZE in {} ms", scale.name(), System.currentTimeMillis() - started);
    }

    private static List<String> statements(Scale s) {
        return TEMPLATES.stream().map(t -> t
                .replace("{PRODUCTS}", Integer.toString(s.products()))
                .replace("{VARIANTS_PER}", Integer.toString(s.variantsPer()))
                .replace("{REQUESTS}", Integer.toString(s.requests()))
        ).toList();
    }

    private static final List<String> TEMPLATES = List.of(
            // ── Бренды и категории (2 уровня; фиксированы, объёма достаточно для фильтра) ──
            """
            insert into brands (slug, name)
            select 'load-brand-' || g, 'Load Brand ' || g from generate_series(1, 8) g
            on conflict (slug) do nothing
            """,
            """
            insert into categories (slug, sort_order)
            select 'load-cat-r' || g, g from generate_series(1, 3) g
            on conflict (slug) do nothing
            """,
            """
            insert into categories (parent_id, slug, sort_order)
            select r.id, 'load-cat-' || r.slug || '-c' || g, g
            from categories r, generate_series(1, 4) g
            where r.slug like 'load-cat-r%'
            on conflict (slug) do nothing
            """,
            // ── Товары ─────────────────────────────────────────────────────────
            """
            with cats as (select array_agg(id) ids from categories where slug like 'load-cat-%-c%'),
                 brs  as (select array_agg(id) ids from brands where slug like 'load-brand-%')
            insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type,
                                  coverage_sqm_per_liter, drying_time_hours, is_active, is_featured, created_at)
            select cats.ids[1 + (g % array_length(cats.ids, 1))],
                   brs.ids[1 + (g % array_length(brs.ids, 1))],
                   'LOAD-' || g,
                   (array['INTERIOR','EXTERIOR','WOOD','METAL','PRIMER','ENAMEL','OTHER'])[1 + (g % 7)],
                   (array['INDOOR','OUTDOOR','BOTH'])[1 + (g % 3)],
                   (array['MATTE','SATIN','GLOSS','SEMI_GLOSS','TEXTURED'])[1 + (g % 5)],
                   (array['WATER','SOLVENT','EPOXY'])[1 + (g % 3)],
                   10 + (g % 5), 2 + (g % 4),
                   (g % 10 <> 0), (g % 7 = 0),
                   now() - ((g % 17520) || ' hours')::interval
            from generate_series(1, {PRODUCTS}) g, cats, brs
            """,
            // ── Переводы товаров (en + he) ─────────────────────────────────────
            """
            insert into product_translations (product_id, locale, name)
            select p.id, 'en', 'Load Paint ' || p.id from products p where p.sku like 'LOAD-%'
            on conflict (product_id, locale) do nothing
            """,
            """
            insert into product_translations (product_id, locale, name)
            select p.id, 'he', 'צבע ' || p.id from products p where p.sku like 'LOAD-%'
            on conflict (product_id, locale) do nothing
            """,
            // ── Варианты (variantsPer на товар) ────────────────────────────────
            """
            insert into product_variants (product_id, sku, color_code, color_hex, volume_liters, price, is_active)
            select p.id, 'LOAD-' || p.id || '-' || v,
                   'RAL' || (1000 + ((p.id * 7 + v * 13) % 9000)),
                   '#' || lpad(to_hex((p.id * v * 997) % 16777215), 6, '0'),
                   (array[0.75, 1.0, 2.5, 3.0, 5.0, 10.0])[v],
                   (20 + ((p.id + v * 17) % 300))::numeric(12,2),
                   ((p.id + v) % 20 <> 0)
            from products p, generate_series(1, {VARIANTS_PER}) v
            where p.sku like 'LOAD-%'
            """,
            // ── 3 склада ───────────────────────────────────────────────────────
            """
            insert into warehouses (code, name_translations, is_pickup_point, is_active, sort_order)
            select 'LOAD-WH-' || g, jsonb_build_object('en', 'Load Warehouse ' || g), (g = 1), true, g
            from generate_series(1, 3) g
            on conflict (code) do nothing
            """,
            // ── Остатки: вариант × склад ───────────────────────────────────────
            """
            insert into inventory (variant_id, warehouse_id, qty, reserved_qty)
            select v.id, w.id, ((v.id * 31 + w.id * 7) % 200), 0
            from product_variants v, warehouses w
            where v.sku like 'LOAD-%' and w.code like 'LOAD-WH-%'
            """,
            // ── Заявки на услуги ───────────────────────────────────────────────
            """
            insert into service_requests (request_number, customer_name, customer_phone, property_type,
                                          status, source, city, area_sqm, consent_given_at, created_at)
            select 'SR-LOAD-' || lpad(g::text, 7, '0'), 'Load Client ' || g, '+97250' || lpad(g::text, 7, '0'),
                   (array['APARTMENT','HOUSE','OFFICE','COMMERCIAL','OTHER'])[1 + (g % 5)],
                   (array['NEW','CONTACTED','MEASUREMENT_SCHEDULED','MEASURED','QUOTED',
                          'ACCEPTED','IN_PROGRESS','COMPLETED','REJECTED','LOST'])[1 + (g % 10)],
                   (array['SITE_FORM','PHONE','WHATSAPP'])[1 + (g % 3)],
                   (array['Tel Aviv','Haifa','Jerusalem','Netanya'])[1 + (g % 4)],
                   (30 + (g % 200))::numeric(8,2),
                   now() - ((g % 17520) || ' hours')::interval,
                   now() - ((g % 17520) || ' hours')::interval
            from generate_series(1, {REQUESTS}) g
            on conflict (request_number) do nothing
            """
    );
}
