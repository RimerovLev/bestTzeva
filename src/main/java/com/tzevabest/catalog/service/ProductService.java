package com.tzevabest.catalog.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.catalog.domain.Brand;
import com.tzevabest.catalog.domain.Category;
import com.tzevabest.catalog.domain.Product;
import com.tzevabest.catalog.domain.ProductAttribute;
import com.tzevabest.catalog.domain.ProductAttributeValue;
import com.tzevabest.catalog.domain.ProductTranslation;
import com.tzevabest.catalog.domain.ProductVariant;
import com.tzevabest.catalog.dto.AttributeInput;
import com.tzevabest.catalog.dto.BulkPriceRequest;
import com.tzevabest.catalog.dto.ProductListItem;
import com.tzevabest.catalog.dto.ProductResponse;
import com.tzevabest.catalog.dto.ProductSaveRequest;
import com.tzevabest.catalog.dto.ProductTranslationInput;
import com.tzevabest.catalog.dto.VariantInput;
import com.tzevabest.catalog.dto.VariantPriceRequest;
import com.tzevabest.catalog.dto.VariantResponse;
import com.tzevabest.catalog.repository.BrandRepository;
import com.tzevabest.catalog.repository.CategoryRepository;
import com.tzevabest.catalog.repository.ProductAttributeRepository;
import com.tzevabest.catalog.repository.ProductAttributeValueRepository;
import com.tzevabest.catalog.repository.ProductRepository;
import com.tzevabest.catalog.repository.ProductTranslationRepository;
import com.tzevabest.catalog.repository.ProductVariantRepository;
import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.dto.SortRequest;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.common.util.Slugs;
import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Управление товарами-агрегатами: создание/обновление целиком, точечные операции (цена варианта,
 * активность товара, массовое изменение цен), фильтруемый список, мягкое/жёсткое удаление.
 * Денормализованные min_price/max_price/has_stock ведёт триггер БД на product_variants (в т.ч. при
 * массовом UPDATE). Каждое изменение пишется в audit_log с diff.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductTranslationRepository translations;
    private final ProductAttributeRepository attributes;
    private final ProductAttributeValueRepository attributeValues;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final JdbcTemplate jdbc;

    private static final Set<String> SORTABLE = Set.of("created_at", "sku", "min_price", "max_price");

    // ── Создание / обновление агрегата ───────────────────────────────────────────

    @Transactional
    public ProductResponse create(ProductSaveRequest request) {
        validateAggregate(request);
        requireProductSkuFree(request.sku(), null);
        for (VariantInput vi : request.variants()) {
            requireVariantSkuFree(vi.sku(), null);
        }
        Product product = new Product();
        applyProductFields(product, request);
        product.setSlug(resolveProductSlug(request, null));
        products.save(product);
        saveTranslations(product, request);
        saveAttributes(product, request.attributes());
        for (VariantInput vi : request.variants()) {
            ProductVariant v = new ProductVariant();
            applyVariantFields(v, product, vi);
            variants.save(v);
        }
        variants.flush(); // триггер пересчитает денормализованные поля
        writeAudit("PRODUCT_CREATE", product.getId(), Map.of("created", true, "sku", product.getSku()));
        return load(product.getId());
    }

    @Transactional
    public ProductResponse update(long id, ProductSaveRequest request) {
        Product product = products.findById(id)
                .orElseThrow(() -> new NotFoundException("catalog.product.not_found"));
        validateAggregate(request);
        requireProductSkuFree(request.sku(), id);

        Map<String, Object> before = productSnapshot(product);
        applyProductFields(product, request);
        applySlugOnUpdate(product, request);
        products.save(product);

        translations.deleteByProduct_Id(id);
        translations.flush();
        saveTranslations(product, request);

        attributeValues.deleteByProduct_Id(id);
        attributeValues.flush();
        saveAttributes(product, request.attributes());

        reconcileVariants(product, request.variants());
        variants.flush();

        if (product.isActive() && variants.countByProduct_IdAndActiveTrue(id) == 0) {
            throw new ValidationException("catalog.product.no_active_variant");
        }
        writeAudit("PRODUCT_UPDATE", id, diff(before, productSnapshot(product)));
        return load(id);
    }

    private void reconcileVariants(Product product, List<VariantInput> inputs) {
        List<ProductVariant> existing = variants.findByProduct_Id(product.getId());
        Map<Long, ProductVariant> byId = new LinkedHashMap<>();
        existing.forEach(v -> byId.put(v.getId(), v));
        Set<Long> referenced = new HashSet<>();

        for (VariantInput vi : inputs) {
            if (vi.id() != null) {
                ProductVariant v = byId.get(vi.id());
                if (v == null) {
                    throw new NotFoundException("catalog.product.not_found");
                }
                requireVariantSkuFree(vi.sku(), v.getId());
                applyVariantFields(v, product, vi);
                variants.save(v);
                referenced.add(vi.id());
            } else {
                requireVariantSkuFree(vi.sku(), null);
                ProductVariant v = new ProductVariant();
                applyVariantFields(v, product, vi);
                variants.save(v);
            }
        }
        // Варианты, отсутствующие в запросе: с историей обращений — деактивируются, иначе удаляются.
        for (ProductVariant v : existing) {
            if (!referenced.contains(v.getId())) {
                if (hasPurchaseHistory(v.getId())) {
                    v.setActive(false);
                    variants.save(v);
                } else {
                    variants.delete(v);
                }
            }
        }
    }

    // ── Точечные операции ────────────────────────────────────────────────────────

    @Transactional
    public VariantResponse changeVariantPrice(long variantId, VariantPriceRequest request) {
        ProductVariant v = variants.findById(variantId)
                .orElseThrow(() -> new NotFoundException("catalog.product.not_found"));
        if (request.oldPrice() != null && request.oldPrice().compareTo(request.price()) <= 0) {
            throw new ValidationException("catalog.variant.old_price_invalid");
        }
        Map<String, Object> before = Map.of("price", v.getPrice(), "old_price", String.valueOf(v.getOldPrice()));
        v.setPrice(request.price());
        v.setOldPrice(request.oldPrice());
        variants.saveAndFlush(v); // триггер пересчитает min/max
        writeAudit("VARIANT_PRICE", v.getProduct().getId(),
                diff(before, Map.of("price", v.getPrice(), "old_price", String.valueOf(v.getOldPrice()))));
        return toVariantResponse(v);
    }

    @Transactional
    public ProductResponse changeActive(long id, boolean active) {
        Product product = products.findById(id)
                .orElseThrow(() -> new NotFoundException("catalog.product.not_found"));
        if (active && variants.countByProduct_IdAndActiveTrue(id) == 0) {
            throw new ValidationException("catalog.product.no_active_variant");
        }
        boolean before = product.isActive();
        product.setActive(active);
        products.saveAndFlush(product);
        writeAudit("PRODUCT_ACTIVE", id, diff(Map.of("active", before), Map.of("active", active)));
        return load(id);
    }

    /** Массовое изменение цен по фильтру. UPDATE идёт по product_variants — триггер денорм срабатывает. */
    @Transactional
    public int bulkChangePrices(BulkPriceRequest request) {
        BigDecimal multiplier = BigDecimal.ONE.add(request.percent().movePointLeft(2));
        StringBuilder sql = new StringBuilder(
                "update product_variants v set price = round(v.price * ?, 2) from products p "
                        + "where v.product_id = p.id");
        List<Object> args = new ArrayList<>();
        args.add(multiplier);
        if (request.brandId() != null) {
            sql.append(" and p.brand_id = ?");
            args.add(request.brandId());
        }
        if (request.categoryId() != null) {
            sql.append(" and p.category_id = ?");
            args.add(request.categoryId());
        }
        if (request.paintType() != null) {
            sql.append(" and p.paint_type = ?");
            args.add(request.paintType().name());
        }
        int affected = jdbc.update(sql.toString(), args.toArray());
        writeAudit("PRODUCT_BULK_PRICE", null, Map.of(
                "percent", request.percent(),
                "brand_id", String.valueOf(request.brandId()),
                "category_id", String.valueOf(request.categoryId()),
                "paint_type", String.valueOf(request.paintType()),
                "affected_variants", affected));
        return affected;
    }

    // ── Удаление ─────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(long id, boolean hard) {
        Product product = products.findById(id)
                .orElseThrow(() -> new NotFoundException("catalog.product.not_found"));
        if (hard) {
            if (hasPurchaseHistory(product)) {
                throw new ConflictException("catalog.product.in_orders");
            }
            products.delete(product); // варианты/переводы/атрибуты — каскадом FK
            writeAudit("PRODUCT_DELETE_HARD", id, Map.of("sku", product.getSku()));
        } else {
            product.setActive(false);
            products.saveAndFlush(product);
            writeAudit("PRODUCT_DELETE_SOFT", id, Map.of("active", false));
        }
    }

    // ── Список с фильтрами ───────────────────────────────────────────────────────

    public PageResponse<ProductListItem> list(Long categoryId, Long brandId, PaintType paintType,
                                              Boolean active, Boolean inStock,
                                              BigDecimal priceMin, BigDecimal priceMax,
                                              String query, PageQuery page) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (categoryId != null) {
            where.append(" and p.category_id = ?");
            args.add(categoryId);
        }
        if (brandId != null) {
            where.append(" and p.brand_id = ?");
            args.add(brandId);
        }
        if (paintType != null) {
            where.append(" and p.paint_type = ?");
            args.add(paintType.name());
        }
        if (active != null) {
            where.append(" and p.is_active = ?");
            args.add(active);
        }
        if (inStock != null) {
            where.append(" and p.has_stock = ?");
            args.add(inStock);
        }
        if (priceMin != null) {
            where.append(" and p.max_price >= ?");
            args.add(priceMin);
        }
        if (priceMax != null) {
            where.append(" and p.min_price <= ?");
            args.add(priceMax);
        }
        if (query != null && !query.isBlank()) {
            where.append(" and (p.sku ilike ? or exists (select 1 from product_translations t "
                    + "where t.product_id = p.id and t.name ilike ?))");
            args.add("%" + query.trim() + "%");
            args.add("%" + query.trim() + "%");
        }

        Long total = jdbc.queryForObject("select count(*) from products p" + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;

        String orderBy = orderBy(page.sort());
        String sql = "select p.id, p.sku, p.category_id, p.brand_id, p.paint_type, p.is_active, "
                + "p.min_price, p.max_price, p.has_stock, "
                + "(select name from product_translations t where t.product_id = p.id "
                + " order by case t.locale when 'he' then 0 when 'en' then 1 else 2 end limit 1) as name "
                + "from products p" + where + orderBy + " limit ? offset ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page.size());
        pageArgs.add((long) page.page() * page.size());

        List<ProductListItem> items = jdbc.query(sql, (rs, i) -> new ProductListItem(
                rs.getLong("id"), rs.getString("sku"), rs.getLong("category_id"), rs.getLong("brand_id"),
                PaintType.valueOf(rs.getString("paint_type")), rs.getBoolean("is_active"),
                rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"), rs.getBoolean("has_stock"),
                rs.getString("name")), pageArgs.toArray());

        return PageResponse.of(items, page.page(), page.size(), totalElements);
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

    // ── Загрузка ответа (JdbcTemplate: денормализованные поля + свежее состояние) ─

    public ProductResponse load(long id) {
        Map<String, ProductTranslationInput> tr = new LinkedHashMap<>();
        jdbc.query("select locale, name, short_description, description, usage_instructions, "
                + "meta_title, meta_description from product_translations where product_id = ?",
                (RowCallbackHandler) rs -> tr.put(rs.getString("locale"), new ProductTranslationInput(
                        rs.getString("name"), rs.getString("short_description"), rs.getString("description"),
                        rs.getString("usage_instructions"), rs.getString("meta_title"),
                        rs.getString("meta_description"))), id);

        Map<String, String> attrs = new LinkedHashMap<>();
        jdbc.query("select a.code, v.value from product_attribute_values v "
                + "join product_attributes a on a.id = v.attribute_id where v.product_id = ?",
                (RowCallbackHandler) rs -> attrs.put(rs.getString("code"), rs.getString("value")), id);

        List<VariantResponse> variantList = jdbc.query(
                "select id, sku, color_code, color_hex, volume_liters, weight_kg, price, old_price, "
                        + "barcode, is_active from product_variants where product_id = ? order by id",
                (rs, i) -> new VariantResponse(rs.getLong("id"), rs.getString("sku"), rs.getString("color_code"),
                        rs.getString("color_hex"), rs.getBigDecimal("volume_liters"), rs.getBigDecimal("weight_kg"),
                        rs.getBigDecimal("price"), rs.getBigDecimal("old_price"), rs.getString("barcode"),
                        rs.getBoolean("is_active")), id);

        return jdbc.queryForObject(
                "select slug, sku, category_id, brand_id, paint_type, usage_area, finish, base_type, "
                        + "coverage_sqm_per_liter, drying_time_hours, is_active, is_featured, "
                        + "min_price, max_price, has_stock from products where id = ?",
                (rs, i) -> new ProductResponse(id, rs.getString("slug"), rs.getString("sku"), rs.getLong("category_id"),
                        rs.getLong("brand_id"), PaintType.valueOf(rs.getString("paint_type")),
                        UsageArea.valueOf(rs.getString("usage_area")), Finish.valueOf(rs.getString("finish")),
                        BaseType.valueOf(rs.getString("base_type")), rs.getBigDecimal("coverage_sqm_per_liter"),
                        rs.getBigDecimal("drying_time_hours"), rs.getBoolean("is_active"), rs.getBoolean("is_featured"),
                        rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"), rs.getBoolean("has_stock"),
                        tr, attrs, variantList), id);
    }

    // ── Валидация ────────────────────────────────────────────────────────────────

    private void validateAggregate(ProductSaveRequest request) {
        if (!categories.existsById(request.categoryId()) || !brands.existsById(request.brandId())) {
            throw new ValidationException("catalog.product.reference_not_found");
        }
        Set<String> variantSkus = new HashSet<>();
        int activeVariants = 0;
        for (VariantInput vi : request.variants()) {
            if (!variantSkus.add(vi.sku())) {
                throw new ConflictException("catalog.sku.duplicate");
            }
            if (vi.oldPrice() != null && vi.oldPrice().compareTo(vi.price()) <= 0) {
                throw new ValidationException("catalog.variant.old_price_invalid");
            }
            if (vi.active() == null || vi.active()) {
                activeVariants++;
            }
        }
        boolean productActive = request.active() == null || request.active();
        if (productActive && activeVariants == 0) {
            throw new ValidationException("catalog.product.no_active_variant");
        }
    }

    private void requireProductSkuFree(String sku, Long excludeId) {
        boolean taken = products.findBySku(sku)
                .map(p -> excludeId == null || !p.getId().equals(excludeId)).orElse(false);
        if (taken) {
            throw new ConflictException("catalog.sku.duplicate");
        }
    }

    private void requireVariantSkuFree(String sku, Long excludeId) {
        boolean taken = variants.findBySku(sku)
                .map(v -> excludeId == null || !v.getId().equals(excludeId)).orElse(false);
        if (taken) {
            throw new ConflictException("catalog.sku.duplicate");
        }
    }

    // ── Применение полей ─────────────────────────────────────────────────────────

    // ── slug (B-02) ──────────────────────────────────────────────────────────────

    /** slug на создании: из явного поля или английского названия (иначе HE), с суффиксом при коллизии. */
    private String resolveProductSlug(ProductSaveRequest request, Long excludeId) {
        String explicit = request.slug();
        String source = (explicit != null && !explicit.isBlank()) ? explicit : slugSourceName(request);
        String base = Slugs.slugify(source);
        return Slugs.unique(base, "product", candidate -> slugTaken(candidate, excludeId));
    }

    private static String slugSourceName(ProductSaveRequest request) {
        ProductTranslationInput en = request.translations().en();
        if (en != null && en.name() != null && !en.name().isBlank()) {
            return en.name();
        }
        return request.translations().he().name();
    }

    /** При ручной смене slug: старый адрес уходит в историю (для 301), ставится новый уникальный. */
    private void applySlugOnUpdate(Product product, ProductSaveRequest request) {
        if (request.slug() == null || request.slug().isBlank()) {
            return; // slug не прислали — оставляем как есть
        }
        String desired = Slugs.slugify(request.slug());
        if (desired.isBlank() || desired.equals(product.getSlug())) {
            return; // без изменений
        }
        String unique = Slugs.unique(desired, "product", c -> slugTaken(c, product.getId()));
        jdbc.update("insert into product_slug_history (slug, product_id) values (?, ?) "
                        + "on conflict (slug) do update set product_id = excluded.product_id, created_at = now()",
                product.getSlug(), product.getId());
        product.setSlug(unique);
    }

    /** slug занят другим товаром? (уникальность по всей таблице products, исключая текущий на апдейте.) */
    private boolean slugTaken(String slug, Long excludeId) {
        List<Long> ids = jdbc.query("select id from products where slug = ?", (rs, n) -> rs.getLong("id"), slug);
        if (ids.isEmpty()) {
            return false;
        }
        return excludeId == null || !ids.get(0).equals(excludeId);
    }

    private void applyProductFields(Product product, ProductSaveRequest request) {
        product.setCategory(categories.getReferenceById(request.categoryId()));
        product.setBrand(brands.getReferenceById(request.brandId()));
        product.setSku(request.sku());
        product.setPaintType(request.paintType());
        product.setUsageArea(request.usageArea());
        product.setFinish(request.finish());
        product.setBaseType(request.baseType());
        product.setCoverageSqmPerLiter(request.coverageSqmPerLiter());
        product.setDryingTimeHours(request.dryingTimeHours());
        product.setFeatured(request.featured() != null && request.featured());
        product.setActive(request.active() == null || request.active());
    }

    private void applyVariantFields(ProductVariant v, Product product, VariantInput vi) {
        v.setProduct(product);
        v.setSku(vi.sku());
        v.setColorCode(vi.colorCode());
        v.setColorHex(vi.colorHex());
        v.setVolumeLiters(vi.volumeLiters());
        v.setWeightKg(vi.weightKg());
        v.setPrice(vi.price());
        v.setOldPrice(vi.oldPrice());
        v.setBarcode(vi.barcode());
        v.setActive(vi.active() == null || vi.active());
    }

    private void saveTranslations(Product product, ProductSaveRequest request) {
        Map<String, ProductTranslationInput> byLocale = new LinkedHashMap<>();
        byLocale.put("he", request.translations().he());
        if (request.translations().ru() != null) {
            byLocale.put("ru", request.translations().ru());
        }
        if (request.translations().en() != null) {
            byLocale.put("en", request.translations().en());
        }
        byLocale.forEach((locale, input) -> {
            ProductTranslation t = new ProductTranslation();
            t.setProduct(product);
            t.setLocale(locale);
            t.setName(input.name());
            t.setShortDescription(input.shortDescription());
            t.setDescription(input.description());
            t.setUsageInstructions(input.usageInstructions());
            t.setMetaTitle(input.metaTitle());
            t.setMetaDescription(input.metaDescription());
            translations.save(t);
        });
    }

    private void saveAttributes(Product product, List<AttributeInput> inputs) {
        if (inputs == null) {
            return;
        }
        for (AttributeInput ai : inputs) {
            ProductAttribute attribute = attributes.findByCode(ai.code()).orElseGet(() -> {
                ProductAttribute created = new ProductAttribute();
                created.setCode(ai.code());
                return attributes.save(created);
            });
            ProductAttributeValue value = new ProductAttributeValue();
            value.setProduct(product);
            value.setAttribute(attribute);
            value.setValue(ai.value());
            attributeValues.save(value);
        }
    }

    // ── Аудит / diff ─────────────────────────────────────────────────────────────

    private void writeAudit(String action, Long productId, Map<String, Object> changes) {
        Long actorId = currentUser.optional().map(p -> p.userId()).orElse(null);
        audit.record(actorId, currentUser.ip(), action, "product", productId, changes);
    }

    private Map<String, Object> productSnapshot(Product p) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("sku", p.getSku());
        s.put("active", p.isActive());
        s.put("featured", p.isFeatured());
        s.put("paint_type", p.getPaintType().name());
        s.put("category_id", p.getCategory().getId());
        s.put("brand_id", p.getBrand().getId());
        return s;
    }

    private Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String key : after.keySet()) {
            Object oldValue = before.get(key);
            Object newValue = after.get(key);
            if (!java.util.Objects.equals(String.valueOf(oldValue), String.valueOf(newValue))) {
                changes.put(key, Map.of("old", String.valueOf(oldValue), "new", String.valueOf(newValue)));
            }
        }
        return changes;
    }

    /**
     * Участвовал ли вариант/товар в обращениях клиентов. После сноса e-commerce (T-16.1) источника
     * истории нет; T-15.1 перенацелит на product_request_items (on delete restrict). Пока — false:
     * защищать нечего.
     */
    private boolean hasPurchaseHistory(long variantId) {
        return false;
    }

    private boolean hasPurchaseHistory(Product product) {
        return false;
    }

    private VariantResponse toVariantResponse(ProductVariant v) {
        return new VariantResponse(v.getId(), v.getSku(), v.getColorCode(), v.getColorHex(),
                v.getVolumeLiters(), v.getWeightKg(), v.getPrice(), v.getOldPrice(), v.getBarcode(), v.isActive());
    }
}
