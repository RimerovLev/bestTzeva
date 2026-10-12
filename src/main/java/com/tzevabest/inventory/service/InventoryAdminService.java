package com.tzevabest.inventory.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.dto.SortRequest;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.inventory.domain.ReferenceType;
import com.tzevabest.inventory.dto.ActiveReservationView;
import com.tzevabest.inventory.dto.InventoryListItem;
import com.tzevabest.inventory.dto.InventoryStatus;
import com.tzevabest.inventory.dto.LowStockItem;
import com.tzevabest.inventory.dto.MovementContext;
import com.tzevabest.inventory.dto.MovementView;
import com.tzevabest.inventory.dto.ReceiptLine;
import com.tzevabest.inventory.dto.ReceiptRequest;
import com.tzevabest.inventory.dto.StocktakeRequest;
import com.tzevabest.inventory.dto.TransferRequest;
import com.tzevabest.inventory.dto.VariantMovements;
import com.tzevabest.inventory.dto.WriteOffRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Складское админ-API: список остатков с фильтрами, массовый приход, списание/инвентаризация/
 * перемещение, журнал движений с резервами, отчёт «ниже порога», экспорт XLSX. Все изменяющие
 * операции идут через {@link InventoryService} (журнал/денормализация/события) и пишутся в
 * audit_log. Точные числа наружу отдаём (ограничение R-47 — только для публичного каталога).
 */
@Service
@RequiredArgsConstructor
public class InventoryAdminService {

    private final JdbcTemplate jdbc;
    private final InventoryService inventory;
    private final AuditService audit;
    private final CurrentUser currentUser;

    private static final String NAME_SUBSELECT =
            "(select name from product_translations t where t.product_id = p.id "
                    + "order by case t.locale when 'he' then 0 when 'en' then 1 else 2 end limit 1)";
    /*
     * Строка списка — пара «вариант × склад». Остатки берутся левым соединением: вариант, который ни
     * разу не приходовали, складской записи не имеет, но для склада это «нет в наличии» и должно
     * попадать в список (и в фильтр «Нет»). Пары без записи показываем только для активных
     * варианта, товара и склада; пары с записью — всегда (остаток на скрытом товаре тоже остаток).
     */
    private static final String BASE_FROM =
            " from product_variants v "
                    + "join products p on p.id = v.product_id "
                    + "cross join warehouses w "
                    + "left join inventory i on i.variant_id = v.id and i.warehouse_id = w.id";
    private static final String PRESENT = "(i.id is not null or (v.is_active and p.is_active and w.is_active))";
    private static final String QTY = "coalesce(i.qty, 0)";
    private static final String RESERVED = "coalesce(i.reserved_qty, 0)";
    private static final String AVAILABLE = "(" + QTY + " - " + RESERVED + ")";

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "sku", "v.sku", "qty", QTY, "reserved", RESERVED, "available", AVAILABLE);

    // ── Список остатков ───────────────────────────────────────────────────────────

    public PageResponse<InventoryListItem> list(Long warehouseId, Long categoryId, Long brandId,
                                                InventoryStatus status, String q, PageQuery page) {
        Filter f = buildFilter(warehouseId, categoryId, brandId, status, q);

        Long total = jdbc.queryForObject("select count(*)" + BASE_FROM + f.where, Long.class, f.args.toArray());
        long totalElements = total == null ? 0 : total;

        String sql = "select v.id variant_id, v.sku, " + NAME_SUBSELECT + " product_name, "
                + "w.id warehouse_id, w.code warehouse_code, p.category_id, p.brand_id, "
                + QTY + " qty, " + RESERVED + " reserved_qty, " + AVAILABLE + " available, i.low_stock_threshold"
                + BASE_FROM + f.where + orderBy(page.sort(), "v.sku") + " limit ? offset ?";
        List<Object> args = new ArrayList<>(f.args);
        args.add(page.size());
        args.add((long) page.page() * page.size());

        List<InventoryListItem> items = jdbc.query(sql, (rs, n) -> {
            int available = rs.getInt("available");
            Integer threshold = (Integer) rs.getObject("low_stock_threshold");
            return new InventoryListItem(rs.getLong("variant_id"), rs.getString("sku"),
                    rs.getString("product_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_code"),
                    (Long) rs.getObject("category_id"), (Long) rs.getObject("brand_id"),
                    rs.getInt("qty"), rs.getInt("reserved_qty"), available, threshold,
                    statusOf(available, threshold));
        }, args.toArray());

        return PageResponse.of(items, page.page(), page.size(), totalElements);
    }

    // ── Массовый приход ───────────────────────────────────────────────────────────

    @Transactional
    public int receipt(ReceiptRequest req) {
        requireWarehouse(req.warehouseId());
        Map<String, Long> variantBySku = resolveSkus(req.lines());
        MovementContext ctx = new MovementContext(ReferenceType.MANUAL, null, actorId());
        for (ReceiptLine line : req.lines()) {
            inventory.receive(variantBySku.get(line.sku()), req.warehouseId(), line.qty(), ctx, req.comment());
        }
        audit("inventory.receipt", "warehouse", req.warehouseId(),
                Map.of("lines", req.lines().size(), "comment", req.comment() == null ? "" : req.comment()));
        return req.lines().size();
    }

    // ── Списание / инвентаризация / перемещение ──────────────────────────────────

    @Transactional
    public void writeOff(WriteOffRequest req) {
        inventory.writeOff(req.variantId(), req.warehouseId(), req.qty(), req.reason(), ctx());
        audit("inventory.write_off", "product_variant", req.variantId(),
                Map.of("warehouse_id", req.warehouseId(), "qty", req.qty(), "reason", req.reason()));
    }

    @Transactional
    public void stocktake(StocktakeRequest req) {
        inventory.stocktake(req.variantId(), req.warehouseId(), req.countedQty(), ctx());
        audit("inventory.stocktake", "product_variant", req.variantId(),
                Map.of("warehouse_id", req.warehouseId(), "counted_qty", req.countedQty(), "reason", req.reason()));
    }

    @Transactional
    public void transfer(TransferRequest req) {
        inventory.transfer(req.variantId(), req.fromWarehouseId(), req.toWarehouseId(), req.qty(), ctx());
        audit("inventory.transfer", "product_variant", req.variantId(),
                Map.of("from_warehouse_id", req.fromWarehouseId(), "to_warehouse_id", req.toWarehouseId(),
                        "qty", req.qty()));
    }

    // ── Журнал движений варианта + активные резервы ──────────────────────────────

    public VariantMovements movements(long variantId, String type, Instant from, Instant to, PageQuery page) {
        StringBuilder where = new StringBuilder(" where variant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(variantId);
        if (type != null && !type.isBlank()) {
            where.append(" and type = ?");
            args.add(type.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (from != null) {
            where.append(" and created_at >= ?");
            args.add(java.sql.Timestamp.from(from));
        }
        if (to != null) {
            where.append(" and created_at < ?");
            args.add(java.sql.Timestamp.from(to));
        }

        Long total = jdbc.queryForObject("select count(*) from inventory_movements" + where,
                Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page.size());
        pageArgs.add((long) page.page() * page.size());
        List<MovementView> movements = jdbc.query("select id, type, qty_delta, qty_after, reserved_delta, "
                        + "reserved_after, reference_type, reference_id, comment, created_by, created_at "
                        + "from inventory_movements" + where + " order by created_at desc, id desc limit ? offset ?",
                (rs, n) -> new MovementView(rs.getLong("id"), rs.getString("type"), rs.getInt("qty_delta"),
                        rs.getInt("qty_after"), rs.getInt("reserved_delta"), (Integer) rs.getObject("reserved_after"),
                        rs.getString("reference_type"), (Long) rs.getObject("reference_id"), rs.getString("comment"),
                        (Long) rs.getObject("created_by"), rs.getTimestamp("created_at").toInstant()),
                pageArgs.toArray());

        List<ActiveReservationView> reservations = jdbc.query(
                "select r.id, r.request_id, pr.request_number, r.warehouse_id, r.qty, r.expires_at "
                        + "from stock_reservations r join product_requests pr on pr.id = r.request_id "
                        + "where r.variant_id = ? and r.status = 'ACTIVE' order by r.expires_at",
                (rs, n) -> new ActiveReservationView(rs.getLong("id"), rs.getLong("request_id"),
                        rs.getString("request_number"), rs.getLong("warehouse_id"), rs.getInt("qty"),
                        rs.getTimestamp("expires_at").toInstant()),
                variantId);

        return new VariantMovements(PageResponse.of(movements, page.page(), page.size(), totalElements),
                reservations);
    }

    // ── Ниже порога ───────────────────────────────────────────────────────────────

    public List<LowStockItem> lowStock() {
        return jdbc.query("select v.id variant_id, v.sku, " + NAME_SUBSELECT + " product_name, "
                        + "w.id warehouse_id, w.code warehouse_code, i.qty, i.reserved_qty, "
                        + AVAILABLE + " available, i.low_stock_threshold"
                        + BASE_FROM
                        + " where i.low_stock_threshold is not null and " + AVAILABLE + " <= i.low_stock_threshold"
                        // критичность = available/threshold; меньше — критичнее (threshold >= available >= 0 > 0)
                        + " order by (" + AVAILABLE + "::float / i.low_stock_threshold) asc, v.sku",
                (rs, n) -> {
                    int available = rs.getInt("available");
                    int threshold = rs.getInt("low_stock_threshold");
                    return new LowStockItem(rs.getLong("variant_id"), rs.getString("sku"),
                            rs.getString("product_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_code"),
                            rs.getInt("qty"), rs.getInt("reserved_qty"), available, threshold,
                            (double) available / threshold);
                });
    }

    /** Все строки остатков для экспорта (те же фильтры, без пагинации). */
    public List<InventoryListItem> forExport(Long warehouseId, Long categoryId, Long brandId,
                                             InventoryStatus status, String q) {
        Filter f = buildFilter(warehouseId, categoryId, brandId, status, q);
        String sql = "select v.id variant_id, v.sku, " + NAME_SUBSELECT + " product_name, "
                + "w.id warehouse_id, w.code warehouse_code, p.category_id, p.brand_id, "
                + QTY + " qty, " + RESERVED + " reserved_qty, " + AVAILABLE + " available, i.low_stock_threshold"
                + BASE_FROM + f.where + " order by v.sku";
        return jdbc.query(sql, (rs, n) -> {
            int available = rs.getInt("available");
            Integer threshold = (Integer) rs.getObject("low_stock_threshold");
            return new InventoryListItem(rs.getLong("variant_id"), rs.getString("sku"),
                    rs.getString("product_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_code"),
                    (Long) rs.getObject("category_id"), (Long) rs.getObject("brand_id"),
                    rs.getInt("qty"), rs.getInt("reserved_qty"), available, threshold,
                    statusOf(available, threshold));
        }, f.args.toArray());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private record Filter(String where, List<Object> args) {
    }

    private Filter buildFilter(Long warehouseId, Long categoryId, Long brandId,
                               InventoryStatus status, String q) {
        StringBuilder where = new StringBuilder(" where " + PRESENT);
        List<Object> args = new ArrayList<>();
        if (warehouseId != null) {
            where.append(" and w.id = ?");
            args.add(warehouseId);
        }
        if (categoryId != null) {
            where.append(" and p.category_id = ?");
            args.add(categoryId);
        }
        if (brandId != null) {
            where.append(" and p.brand_id = ?");
            args.add(brandId);
        }
        if (status != null) {
            where.append(" and ").append(statusCondition(status));
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (v.sku ilike ? or exists (select 1 from product_translations t "
                    + "where t.product_id = p.id and t.name ilike ?))");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        return new Filter(where.toString(), args);
    }

    private static String statusCondition(InventoryStatus status) {
        return switch (status) {
            case OUT -> AVAILABLE + " <= 0";
            case LOW -> "i.low_stock_threshold is not null and " + AVAILABLE + " > 0 and "
                    + AVAILABLE + " <= i.low_stock_threshold";
            case IN_STOCK -> AVAILABLE + " > 0 and (i.low_stock_threshold is null or "
                    + AVAILABLE + " > i.low_stock_threshold)";
        };
    }

    private static InventoryStatus statusOf(int available, Integer threshold) {
        if (available <= 0) {
            return InventoryStatus.OUT;
        }
        if (threshold != null && available <= threshold) {
            return InventoryStatus.LOW;
        }
        return InventoryStatus.IN_STOCK;
    }

    private static String orderBy(SortRequest sort, String defaultColumn) {
        List<String> parts = new ArrayList<>();
        for (SortRequest.Order o : sort.orders()) {
            String column = SORT_COLUMNS.get(o.property());
            if (column != null) {
                parts.add(column + (o.direction() == SortRequest.Direction.DESC ? " desc" : " asc"));
            }
        }
        return parts.isEmpty() ? " order by " + defaultColumn + " asc" : " order by " + String.join(", ", parts);
    }

    private Map<String, Long> resolveSkus(List<ReceiptLine> lines) {
        List<String> skus = lines.stream().map(ReceiptLine::sku).distinct().toList();
        String placeholders = String.join(",", Collections.nCopies(skus.size(), "?"));
        Map<String, Long> found = new LinkedHashMap<>();
        jdbc.query("select id, sku from product_variants where sku in (" + placeholders + ")",
                (rs, n) -> found.put(rs.getString("sku"), rs.getLong("id")), skus.toArray());
        List<String> missing = skus.stream().filter(s -> !found.containsKey(s)).toList();
        if (!missing.isEmpty()) {
            throw new ValidationException("inventory.sku_unknown", String.join(", ", missing));
        }
        return found;
    }

    private void requireWarehouse(long warehouseId) {
        Integer c = jdbc.queryForObject("select count(*) from warehouses where id = ?", Integer.class, warehouseId);
        if (c == null || c == 0) {
            throw new ValidationException("inventory.warehouse_unknown", warehouseId);
        }
    }

    private MovementContext ctx() {
        return new MovementContext(ReferenceType.MANUAL, null, actorId());
    }

    private Long actorId() {
        return currentUser.optional().map(p -> p.userId()).orElse(null);
    }

    private void audit(String action, String entityType, Long entityId, Map<String, Object> changes) {
        audit.record(actorId(), currentUser.ip(), action, entityType, entityId, changes);
    }
}
