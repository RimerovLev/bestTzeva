package com.tzevabest.catalog.service;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;
import com.tzevabest.catalog.dto.ProductCard;
import com.tzevabest.catalog.dto.ProductFacets;
import com.tzevabest.catalog.dto.ProductSearchResult;
import com.tzevabest.catalog.dto.ProductSummary;
import com.tzevabest.catalog.dto.VariantCard;
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

    /**
     * Публичный листинг каталога: фильтры (категория, бренд, тип/область/финиш/база, цена, наличие),
     * поиск по имени (unaccent — нечувствительно к никуд) и SKU, пагинация/сортировка, плюс фасеты по
     * всей выборке. Показываются только активные товары.
     */
    public ProductSearchResult search(Long categoryId, Long brandId, String paintType, String usageArea,
                                      String finish, String baseType, BigDecimal priceMin, BigDecimal priceMax,
                                      Boolean inStock, String q, String localeParam, PageQuery page) {
        String locale = resolveLocale(localeParam);
        StringBuilder where = new StringBuilder(" where p.is_active");
        List<Object> args = new ArrayList<>();
        addEq(where, args, "p.category_id", categoryId);
        addEq(where, args, "p.brand_id", brandId);
        addEq(where, args, "p.paint_type", paintType);
        addEq(where, args, "p.usage_area", usageArea);
        addEq(where, args, "p.finish", finish);
        addEq(where, args, "p.base_type", baseType);
        if (priceMin != null) {
            where.append(" and p.max_price >= ?");
            args.add(priceMin);
        }
        if (priceMax != null) {
            where.append(" and p.min_price <= ?");
            args.add(priceMax);
        }
        if (Boolean.TRUE.equals(inStock)) {
            where.append(" and p.has_stock");
        }
        if (q != null && !q.isBlank()) {
            // Никуд (огласовки, U+0591..U+05C7) снимаются chr()-диапазоном; unaccent — латинская
            // диакритика. Так поиск нечувствителен и к огласовкам, и к диакритике.
            String stripNiqqud = "regexp_replace(t.name, '[' || chr(1425) || '-' || chr(1479) || ']', '', 'g')";
            where.append(" and (p.sku ilike ? or exists (select 1 from product_translations t "
                    + "where t.product_id = p.id and unaccent(" + stripNiqqud + ") ilike unaccent(?)))");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        String whereSql = where.toString();
        Object[] a = args.toArray();

        Long total = jdbc.queryForObject("select count(*) from products p" + whereSql, Long.class, a);

        String nameExpr = "(select name from product_translations t where t.product_id = p.id "
                + "order by case t.locale when '" + locale + "' then 0 when 'he' then 1 else 2 end limit 1)";
        String sql = "select p.id, p.sku, p.category_id, p.brand_id, p.paint_type, p.min_price, p.max_price, "
                + "p.has_stock, " + nameExpr + " as name from products p" + whereSql
                + orderBy(page.sort()) + " limit ? offset ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page.size());
        pageArgs.add((long) page.page() * page.size());
        List<ProductSummary> items = jdbc.query(sql, (rs, n) -> new ProductSummary(
                rs.getLong("id"), rs.getString("sku"), rs.getLong("category_id"), rs.getLong("brand_id"),
                PaintType.valueOf(rs.getString("paint_type")), rs.getString("name"),
                rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"), rs.getBoolean("has_stock")),
                pageArgs.toArray());

        ProductFacets facets = facets(whereSql, a);
        PageResponse<ProductSummary> productsPage = PageResponse.of(items, page.page(), page.size(),
                total == null ? 0 : total);
        return new ProductSearchResult(productsPage, facets);
    }

    private ProductFacets facets(String whereSql, Object[] args) {
        Map<String, Long> byPaintType = new LinkedHashMap<>();
        jdbc.query("select paint_type, count(*) as c from products p" + whereSql + " group by paint_type",
                (RowCallbackHandler) rs -> byPaintType.put(rs.getString("paint_type"), rs.getLong("c")), args);
        Map<Long, Long> byBrand = new LinkedHashMap<>();
        jdbc.query("select brand_id, count(*) as c from products p" + whereSql + " group by brand_id",
                (RowCallbackHandler) rs -> byBrand.put(rs.getLong("brand_id"), rs.getLong("c")), args);
        BigDecimal priceMin = jdbc.queryForObject("select min(min_price) from products p" + whereSql, BigDecimal.class, args);
        BigDecimal priceMax = jdbc.queryForObject("select max(max_price) from products p" + whereSql, BigDecimal.class, args);
        return new ProductFacets(byPaintType, byBrand, priceMin, priceMax);
    }

    private static void addEq(StringBuilder where, List<Object> args, String column, Object value) {
        if (value != null) {
            where.append(" and ").append(column).append(" = ?");
            args.add(value);
        }
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

        List<VariantCard> variants = jdbc.query("""
                select v.id, v.sku, v.color_code, v.color_hex, v.volume_liters, v.price, v.old_price,
                       coalesce(a.available_qty, 0) as available
                from product_variants v
                left join v_variant_available a on a.variant_id = v.id
                where v.product_id = ? and v.is_active
                order by v.id
                """, (rs, n) -> {
                    int available = rs.getInt("available");
                    return new VariantCard(rs.getLong("id"), rs.getString("sku"), rs.getString("color_code"),
                            rs.getString("color_hex"), rs.getBigDecimal("volume_liters"), rs.getBigDecimal("price"),
                            rs.getBigDecimal("old_price"), available, available > 0);
                }, id);

        try {
            return jdbc.queryForObject("""
                    select sku, category_id, brand_id, paint_type, usage_area, finish, base_type,
                           coverage_sqm_per_liter, drying_time_hours, min_price, max_price, has_stock
                    from products where id = ? and is_active
                    """, (rs, n) -> new ProductCard(id, rs.getString("sku"), rs.getLong("category_id"),
                            rs.getLong("brand_id"), PaintType.valueOf(rs.getString("paint_type")),
                            UsageArea.valueOf(rs.getString("usage_area")), Finish.valueOf(rs.getString("finish")),
                            BaseType.valueOf(rs.getString("base_type")), rs.getBigDecimal("coverage_sqm_per_liter"),
                            rs.getBigDecimal("drying_time_hours"),
                            chosen != null ? chosen[0] : rs.getString("sku"),
                            chosen != null ? chosen[1] : null,
                            chosen != null ? chosen[2] : null,
                            chosen != null ? chosen[3] : null,
                            rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"), rs.getBoolean("has_stock"),
                            variants), id);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("catalog.product.not_found");
        }
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
