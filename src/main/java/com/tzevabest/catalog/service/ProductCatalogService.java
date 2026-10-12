package com.tzevabest.catalog.service;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;
import com.tzevabest.catalog.dto.ProductCard;
import com.tzevabest.catalog.dto.ProductFacets;
import com.tzevabest.catalog.dto.ProductSearchResult;
import com.tzevabest.catalog.dto.ProductSummary;
import com.tzevabest.catalog.dto.CoverageCalcResult;
import com.tzevabest.common.web.error.SlugMovedException;
import com.tzevabest.catalog.dto.ProductMediaView;
import com.tzevabest.catalog.dto.VariantCard;
import com.tzevabest.catalog.domain.MediaType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.dto.SortRequest;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.common.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Публичное чтение каталога товаров (витрина): карточка товара с локализацией, денормализованными
 * ценой/наличием и активными вариантами. Без рейтинга/отзывов.
 */
@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private static final String DEFAULT_LOCALE = "he";
    private static final List<String> LOCALES = List.of("he", "ru", "en");

    private static final Set<String> SORTABLE = Set.of("created_at", "min_price", "max_price", "sku");

    private final JdbcTemplate jdbc;
    private final ObjectMapper jsonMapper;

    @org.springframework.beans.factory.annotation.Value("${app.catalog.stock-few-threshold:10}")
    private int stockFewThreshold;

    /**
     * Публичный листинг каталога: фильтры (категория, бренд, тип/область/финиш/база, цена, наличие),
     * поиск по имени (unaccent — нечувствительно к никуд) и SKU, пагинация/сортировка, плюс фасеты по
     * всей выборке. Показываются только активные товары.
     */
    public ProductSearchResult search(Long categoryId, Long brandId, String paintType, String usageArea,
                                      String finish, String baseType, BigDecimal priceMin, BigDecimal priceMax,
                                      Boolean inStock, String q, String localeParam, PageQuery page) {
        String locale = resolveLocale(localeParam);
        List<Cond> conds = new ArrayList<>();
        eq(conds, "category", "p.category_id", categoryId);
        eq(conds, "brand", "p.brand_id", brandId);
        eq(conds, "paint_type", "p.paint_type", paintType);
        eq(conds, "usage_area", "p.usage_area", usageArea);
        eq(conds, "finish", "p.finish", finish);
        eq(conds, "base_type", "p.base_type", baseType);
        if (priceMin != null) {
            conds.add(new Cond("price", "p.max_price >= ?", List.of(priceMin)));
        }
        if (priceMax != null) {
            conds.add(new Cond("price", "p.min_price <= ?", List.of(priceMax)));
        }
        if (Boolean.TRUE.equals(inStock)) {
            conds.add(new Cond("in_stock", "p.has_stock", List.of()));
        }
        if (q != null && !q.isBlank()) {
            // Ищем по нормализованной колонке name_normalized (никуд снят + unaccent), под которой есть
            // триграммный GIN-индекс (R-48). Запрос нормализуем тем же способом. Шаблон экранирован (R-55).
            String like = "%" + escapeLike(q.trim()) + "%";
            conds.add(new Cond("q", "(p.sku ilike ? escape '\\' or exists (select 1 from product_translations t "
                    + "where t.product_id = p.id and t.name_normalized ilike immutable_unaccent("
                    + "regexp_replace(?, '[' || chr(1425) || '-' || chr(1479) || ']', '', 'g')) escape '\\'))",
                    List.of(like, like)));
        }

        Where all = buildWhere(conds, null);
        Long total = jdbc.queryForObject("select count(*) from products p" + all.sql(), Long.class, all.args().toArray());

        // Локаль — связанным параметром, не конкатенацией (R-54): резолвер сегодня безопасен, но
        // инъекция не должна зависеть от удалённой проверки. Параметр в SELECT идёт до WHERE-аргументов.
        String nameExpr = "(select name from product_translations t where t.product_id = p.id "
                + "order by case t.locale when ? then 0 when 'he' then 1 else 2 end limit 1)";
        String sql = "select p.id, p.slug, p.sku, p.category_id, p.brand_id, p.paint_type, p.min_price, p.max_price, "
                + "p.has_stock, " + nameExpr + " as name from products p" + all.sql()
                + orderBy(page.sort()) + " limit ? offset ?";
        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(locale);
        pageArgs.addAll(all.args());
        pageArgs.add(page.size());
        pageArgs.add((long) page.page() * page.size());
        List<ProductSummary> items = jdbc.query(sql, (rs, n) -> new ProductSummary(
                rs.getLong("id"), rs.getString("slug"), rs.getString("sku"),
                rs.getLong("category_id"), rs.getLong("brand_id"),
                PaintType.valueOf(rs.getString("paint_type")), rs.getString("name"),
                rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"), rs.getBoolean("has_stock")),
                pageArgs.toArray());

        return new ProductSearchResult(
                PageResponse.of(items, page.page(), page.size(), total == null ? 0 : total),
                facets(conds));
    }

    /** Каждый фасет считается со всеми фильтрами, КРОМЕ своего собственного (R-43). */
    private ProductFacets facets(List<Cond> conds) {
        Map<String, Long> byPaintType = new LinkedHashMap<>();
        Where wPaint = buildWhere(conds, "paint_type");
        jdbc.query("select paint_type, count(*) as c from products p" + wPaint.sql() + " group by paint_type",
                (RowCallbackHandler) rs -> byPaintType.put(rs.getString("paint_type"), rs.getLong("c")),
                wPaint.args().toArray());

        Map<Long, Long> byBrand = new LinkedHashMap<>();
        Where wBrand = buildWhere(conds, "brand");
        jdbc.query("select brand_id, count(*) as c from products p" + wBrand.sql() + " group by brand_id",
                (RowCallbackHandler) rs -> byBrand.put(rs.getLong("brand_id"), rs.getLong("c")),
                wBrand.args().toArray());

        Where wPrice = buildWhere(conds, "price");
        BigDecimal priceMin = jdbc.queryForObject("select min(min_price) from products p" + wPrice.sql(),
                BigDecimal.class, wPrice.args().toArray());
        BigDecimal priceMax = jdbc.queryForObject("select max(max_price) from products p" + wPrice.sql(),
                BigDecimal.class, wPrice.args().toArray());
        return new ProductFacets(byPaintType, byBrand, priceMin, priceMax);
    }

    /** Экранирование служебных символов LIKE (R-55): иначе '50%' вернул бы весь каталог. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void eq(List<Cond> conds, String key, String column, Object value) {
        if (value != null) {
            conds.add(new Cond(key, column + " = ?", List.of(value)));
        }
    }

    private static Where buildWhere(List<Cond> conds, String excludeKey) {
        StringBuilder sql = new StringBuilder(" where p.is_active");
        List<Object> args = new ArrayList<>();
        for (Cond c : conds) {
            if (!c.key().equals(excludeKey)) {
                sql.append(" and ").append(c.sql());
                args.addAll(c.args());
            }
        }
        return new Where(sql.toString(), args);
    }

    private record Cond(String key, String sql, List<Object> args) {
    }

    private record Where(String sql, List<Object> args) {
    }

    private String orderBy(SortRequest sort) {
        List<String> parts = new ArrayList<>();
        for (SortRequest.Order o : sort.orders()) {
            if (SORTABLE.contains(o.property())) {
                parts.add("p." + o.property() + (o.direction() == SortRequest.Direction.DESC ? " desc" : " asc"));
            }
        }
        return parts.isEmpty() ? " order by p.created_at desc" : " order by " + String.join(", ", parts);
    }

    /** Карточка активного товара. 404, если товар не найден или неактивен. */
    public ProductCard card(long id, String localeParam) {
        String locale = resolveLocale(localeParam);

        // Переводы: выбранная локаль и HE (подстановка).
        Map<String, String[]> tr = new HashMap<>();
        jdbc.query("select locale, name, short_description, description, usage_instructions "
                        + "from product_translations where product_id = ?",
                (RowCallbackHandler) rs -> tr.put(rs.getString("locale"), new String[]{
                        rs.getString("name"), rs.getString("short_description"),
                        rs.getString("description"), rs.getString("usage_instructions")}), id);
        String[] chosen = tr.getOrDefault(locale, tr.get(DEFAULT_LOCALE));

        record VariantRow(long id, String sku, String colorCode, String colorHex, BigDecimal volume,
                          BigDecimal price, BigDecimal oldPrice, int available) {
        }
        List<VariantRow> variantRows = jdbc.query("""
                select v.id, v.sku, v.color_code, v.color_hex, v.volume_liters, v.price, v.old_price,
                       coalesce(a.available_qty, 0) as available
                from product_variants v
                left join v_variant_available a on a.variant_id = v.id
                where v.product_id = ? and v.is_active
                order by v.id
                """, (rs, n) -> new VariantRow(rs.getLong("id"), rs.getString("sku"), rs.getString("color_code"),
                        rs.getString("color_hex"), rs.getBigDecimal("volume_liters"), rs.getBigDecimal("price"),
                        rs.getBigDecimal("old_price"), rs.getInt("available")), id);

        Map<Long, List<ProductMediaView>> mediaByVariant = variantMedia(
                variantRows.stream().map(VariantRow::id).toList());
        List<VariantCard> variants = variantRows.stream()
                .map(v -> new VariantCard(v.id(), v.sku(), v.colorCode(), v.colorHex(), v.volume(), v.price(),
                        v.oldPrice(), availability(v.available()), v.available() > 0,
                        mediaByVariant.getOrDefault(v.id(), List.of())))
                .toList();

        List<ProductMediaView> productMedia = jdbc.query(
                MEDIA_SELECT + " where product_id = ? order by is_primary desc, sort_order, id",
                this::mapMedia, id);

        try {
            return jdbc.queryForObject("""
                    select slug, sku, category_id, brand_id, paint_type, usage_area, finish, base_type,
                           coverage_sqm_per_liter, drying_time_hours, min_price, max_price, has_stock
                    from products where id = ? and is_active
                    """, (rs, n) -> new ProductCard(id, rs.getString("slug"), rs.getString("sku"),
                            rs.getLong("category_id"),
                            rs.getLong("brand_id"), PaintType.valueOf(rs.getString("paint_type")),
                            UsageArea.valueOf(rs.getString("usage_area")), Finish.valueOf(rs.getString("finish")),
                            BaseType.valueOf(rs.getString("base_type")), rs.getBigDecimal("coverage_sqm_per_liter"),
                            rs.getBigDecimal("drying_time_hours"),
                            chosen != null ? chosen[0] : rs.getString("sku"),
                            chosen != null ? chosen[1] : null,
                            chosen != null ? chosen[2] : null,
                            chosen != null ? chosen[3] : null,
                            rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"), rs.getBoolean("has_stock"),
                            productMedia, variants), id);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("catalog.product.not_found");
        }
    }

    /** Карточка активного товара по slug. Устаревший slug из истории отдаёт 301 (B-02). */
    public ProductCard cardBySlug(String slug, String localeParam) {
        Long id = idBySlug(slug);
        if (id != null) {
            return card(id, localeParam);
        }
        String current = currentSlugForHistorical(slug);
        if (current != null) {
            throw new SlugMovedException("/api/v1/products/" + current);
        }
        throw new NotFoundException("catalog.product.not_found");
    }

    /** Расчёт расхода краски по активному товару (B-04). 404, если товара нет/он неактивен. */
    public CoverageCalcResult coverageCalc(String slug, java.math.BigDecimal area, int coats) {
        Long id = idBySlug(slug);
        if (id == null) {
            throw new NotFoundException("catalog.product.not_found");
        }
        java.math.BigDecimal coverage = jdbc.query(
                "select coverage_sqm_per_liter from products where id = ? and is_active",
                rs -> rs.next() ? rs.getBigDecimal(1) : null, id);
        List<CoverageCalculator.Variant> variants = jdbc.query(
                "select id, sku, color_code, color_hex, volume_liters, price "
                        + "from product_variants where product_id = ? and is_active order by id",
                (rs, n) -> new CoverageCalculator.Variant(rs.getLong("id"), rs.getString("sku"),
                        rs.getString("color_code"), rs.getString("color_hex"),
                        rs.getBigDecimal("volume_liters"), rs.getBigDecimal("price")), id);
        return CoverageCalculator.calculate(coverage, area, coats, variants);
    }

    private Long idBySlug(String slug) {
        List<Long> ids = jdbc.query("select id from products where slug = ? and is_active",
                (rs, n) -> rs.getLong("id"), slug);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String currentSlugForHistorical(String oldSlug) {
        List<String> slugs = jdbc.query(
                "select p.slug from product_slug_history h join products p on p.id = h.product_id "
                        + "where h.slug = ? and p.is_active",
                (rs, n) -> rs.getString("slug"), oldSlug);
        return slugs.isEmpty() ? null : slugs.get(0);
    }

    private static final String MEDIA_SELECT =
            "select id, url, type, sort_order, is_primary, alt_translations from product_media";

    /** Медиа вариантов, сгруппированные по variant_id (главное первым, затем по sort_order). */
    private Map<Long, List<ProductMediaView>> variantMedia(List<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(variantIds.size(), "?"));
        Map<Long, List<ProductMediaView>> byVariant = new HashMap<>();
        jdbc.query("select id, url, type, sort_order, is_primary, alt_translations, variant_id "
                        + "from product_media where variant_id in (" + placeholders
                        + ") order by is_primary desc, sort_order, id",
                (RowCallbackHandler) rs -> byVariant
                        .computeIfAbsent(rs.getLong("variant_id"), k -> new ArrayList<>())
                        .add(mapMedia(rs, 0)),
                variantIds.toArray());
        return byVariant;
    }

    private ProductMediaView mapMedia(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ProductMediaView(rs.getLong("id"), rs.getString("url"),
                MediaType.valueOf(rs.getString("type")), rs.getInt("sort_order"),
                rs.getBoolean("is_primary"), parseAlt(rs.getString("alt_translations")));
    }

    private Map<String, String> parseAlt(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** Градация наличия для витрины (точное число наружу не отдаём, R-47). */
    private String availability(int available) {
        if (available <= 0) {
            return "out_of_stock";
        }
        return available > stockFewThreshold ? "many" : "few";
    }

    private String resolveLocale(String localeParam) {
        if (localeParam != null) {
            String lower = localeParam.toLowerCase(Locale.ROOT);
            if (LOCALES.contains(lower)) {
                return lower;
            }
        }
        return DEFAULT_LOCALE;
    }
}
