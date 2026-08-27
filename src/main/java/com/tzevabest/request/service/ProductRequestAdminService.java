package com.tzevabest.request.service;

import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.dto.SortRequest;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.inventory.dto.ReserveItem;
import com.tzevabest.inventory.service.InventoryService;
import com.tzevabest.notification.service.NotificationSender;
import com.tzevabest.request.domain.ProductRequest;
import com.tzevabest.request.domain.ProductRequestStatus;
import com.tzevabest.request.dto.ProductRequestDetail;
import com.tzevabest.request.dto.ProductRequestListItem;
import com.tzevabest.request.dto.ProductRequestMetrics;
import com.tzevabest.request.repository.ProductRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Работа менеджера с заявками на товар: список с фильтрами, карточка со снимками и текущим состоянием
 * каталога, машина состояний с резервом через {@link InventoryService}, назначение менеджера,
 * внутренние комментарии, метрики воронки.
 */
@Service
@RequiredArgsConstructor
public class ProductRequestAdminService {

    /** Разрешённые переходы. Терминальные статусы (COMPLETED/CANCELLED/LOST) исходящих не имеют. */
    private static final Map<ProductRequestStatus, Set<ProductRequestStatus>> ALLOWED = Map.of(
            ProductRequestStatus.NEW, Set.of(ProductRequestStatus.CONTACTED, ProductRequestStatus.CANCELLED, ProductRequestStatus.LOST),
            ProductRequestStatus.CONTACTED, Set.of(ProductRequestStatus.RESERVED, ProductRequestStatus.CANCELLED, ProductRequestStatus.LOST),
            ProductRequestStatus.RESERVED, Set.of(ProductRequestStatus.COMPLETED, ProductRequestStatus.CANCELLED, ProductRequestStatus.LOST),
            ProductRequestStatus.COMPLETED, Set.of(),
            ProductRequestStatus.CANCELLED, Set.of(),
            ProductRequestStatus.LOST, Set.of());

    private static final Set<String> SORTABLE = Set.of("created_at", "status", "request_number");

    private final ProductRequestRepository requests;
    private final InventoryService inventory;
    private final NotificationSender notifications;
    private final MessageSource messages;
    private final CurrentUser currentUser;
    private final JdbcTemplate jdbc;

    // ── Машина состояний ─────────────────────────────────────────────────────────

    @Transactional
    public ProductRequestDetail transition(long id, ProductRequestStatus to, String comment) {
        ProductRequest request = requests.findById(id)
                .orElseThrow(() -> new NotFoundException("request.not_found"));
        ProductRequestStatus from = request.getStatus();
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new ConflictException("request.transition_forbidden");
        }
        // Побочный эффект по резерву — ровно на заявленных переходах.
        switch (to) {
            case RESERVED -> inventory.reserve(id, reserveItems(id));
            case COMPLETED -> inventory.consume(id);
            case CANCELLED, LOST -> inventory.release(id);
            default -> { }
        }
        request.setStatus(to);
        requests.save(request);
        jdbc.update("""
                insert into product_request_history (request_id, from_status, to_status, changed_by, comment)
                values (?, ?, ?, ?, ?)""",
                id, from.name(), to.name(), actorId(), comment);

        if (to == ProductRequestStatus.CONTACTED || to == ProductRequestStatus.RESERVED) {
            notifyCustomer(request, to);
        }
        return detail(id);
    }

    @Transactional
    public ProductRequestDetail assign(long id, Long managerId) {
        ProductRequest request = requests.findById(id)
                .orElseThrow(() -> new NotFoundException("request.not_found"));
        request.setAssignedManagerId(managerId);
        requests.save(request);
        return detail(id);
    }

    @Transactional
    public void addComment(long id, String text) {
        if (!requests.existsById(id)) {
            throw new NotFoundException("request.not_found");
        }
        jdbc.update("insert into product_request_comments (request_id, author_id, text) values (?, ?, ?)",
                id, actorId(), text);
    }

    private List<ReserveItem> reserveItems(long requestId) {
        return jdbc.query("select variant_id, qty from product_request_items where request_id = ?",
                (rs, n) -> new ReserveItem(rs.getLong("variant_id"), rs.getInt("qty")), requestId);
    }

    private void notifyCustomer(ProductRequest request, ProductRequestStatus to) {
        Locale locale = Locale.forLanguageTag("he");
        String key = "notification.product_request." + to.name().toLowerCase(Locale.ROOT);
        String subject = messages.getMessage(key + ".subject", null, locale);
        String body = messages.getMessage(key + ".body", null, locale);
        if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
            notifications.sendEmail(request.getCustomerEmail(), subject, body);
        } else {
            notifications.sendSms(request.getCustomerPhone(), body);
        }
    }

    private Long actorId() {
        return currentUser.optional().map(p -> p.userId()).orElse(null);
    }

    // ── Карточка ─────────────────────────────────────────────────────────────────

    public ProductRequestDetail detail(long id) {
        ProductRequest r = requests.findById(id)
                .orElseThrow(() -> new NotFoundException("request.not_found"));

        List<ProductRequestDetail.Item> items = jdbc.query("""
                select i.variant_id, i.qty, i.product_name_snapshot, i.sku_snapshot, i.color_snapshot,
                       i.volume_liters_snapshot, i.price_snapshot,
                       v.price as current_price, v.is_active as current_active,
                       coalesce(a.available_qty, 0) as current_available
                from product_request_items i
                left join product_variants v on v.id = i.variant_id
                left join v_variant_available a on a.variant_id = i.variant_id
                where i.request_id = ?
                order by i.id
                """, (rs, n) -> new ProductRequestDetail.Item(
                        rs.getLong("variant_id"), rs.getInt("qty"), rs.getString("product_name_snapshot"),
                        rs.getString("sku_snapshot"), rs.getString("color_snapshot"),
                        rs.getBigDecimal("volume_liters_snapshot"), rs.getBigDecimal("price_snapshot"),
                        rs.getBigDecimal("current_price"), (Integer) rs.getObject("current_available"),
                        (Boolean) rs.getObject("current_active")), id);

        List<ProductRequestDetail.HistoryEntry> history = jdbc.query("""
                select from_status, to_status, changed_by, comment, created_at
                from product_request_history where request_id = ? order by id
                """, (rs, n) -> new ProductRequestDetail.HistoryEntry(
                        rs.getString("from_status"), rs.getString("to_status"), (Long) rs.getObject("changed_by"),
                        rs.getString("comment"), rs.getTimestamp("created_at").toInstant()), id);

        List<ProductRequestDetail.CommentEntry> comments = jdbc.query("""
                select author_id, text, created_at from product_request_comments where request_id = ? order by id
                """, (rs, n) -> new ProductRequestDetail.CommentEntry(
                        (Long) rs.getObject("author_id"), rs.getString("text"),
                        rs.getTimestamp("created_at").toInstant()), id);

        BigDecimal total = items.stream()
                .map(it -> it.snapshotPrice().multiply(BigDecimal.valueOf(it.qty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProductRequestDetail(r.getId(), r.getRequestNumber(), r.getStatus(), r.getCustomerName(),
                r.getCustomerPhone(), r.getCustomerEmail(), r.getComment(), r.getPreferredContactTime(),
                r.getAssignedManagerId(), r.getCreatedAt(), total, items, history, comments);
    }

    // ── Список ───────────────────────────────────────────────────────────────────

    public PageResponse<ProductRequestListItem> list(ProductRequestStatus status, Long managerId,
                                                     LocalDate from, LocalDate to, Boolean inStock,
                                                     String query, PageQuery page) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" and r.status = ?");
            args.add(status.name());
        }
        if (managerId != null) {
            where.append(" and r.assigned_manager_id = ?");
            args.add(managerId);
        }
        if (from != null) {
            where.append(" and r.created_at >= ?");
            args.add(java.sql.Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            where.append(" and r.created_at < ?");
            args.add(java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        if (Boolean.TRUE.equals(inStock)) {
            where.append(" and exists (select 1 from product_request_items i "
                    + "join v_variant_available a on a.variant_id = i.variant_id "
                    + "where i.request_id = r.id and a.available_qty > 0)");
        }
        if (query != null && !query.isBlank()) {
            where.append(" and (r.request_number ilike ? or r.customer_phone ilike ? or r.customer_name ilike ? "
                    + "or exists (select 1 from product_request_items i where i.request_id = r.id and i.sku_snapshot ilike ?))");
            String like = "%" + query.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }

        Long total = jdbc.queryForObject("select count(*) from product_requests r" + where, Long.class, args.toArray());
        String sql = "select r.id, r.request_number, r.status, r.customer_name, r.customer_phone, "
                + "r.assigned_manager_id, r.created_at, "
                + "(select count(*) from product_request_items i where i.request_id = r.id) as item_count "
                + "from product_requests r" + where + orderBy(page.sort()) + " limit ? offset ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page.size());
        pageArgs.add((long) page.page() * page.size());

        List<ProductRequestListItem> items = jdbc.query(sql, (rs, n) -> new ProductRequestListItem(
                rs.getLong("id"), rs.getString("request_number"),
                ProductRequestStatus.valueOf(rs.getString("status")), rs.getString("customer_name"),
                rs.getString("customer_phone"), (Long) rs.getObject("assigned_manager_id"),
                rs.getInt("item_count"), rs.getTimestamp("created_at").toInstant()), pageArgs.toArray());

        return PageResponse.of(items, page.page(), page.size(), total == null ? 0 : total);
    }

    private String orderBy(SortRequest sort) {
        List<String> parts = new ArrayList<>();
        for (SortRequest.Order o : sort.orders()) {
            if (SORTABLE.contains(o.property())) {
                parts.add("r." + o.property() + (o.direction() == SortRequest.Direction.DESC ? " desc" : " asc"));
            }
        }
        return parts.isEmpty() ? " order by r.created_at desc" : " order by " + String.join(", ", parts);
    }

    // ── Метрики воронки ──────────────────────────────────────────────────────────

    public ProductRequestMetrics metrics() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("select status, count(*) as c from product_requests group by status",
                (RowCallbackHandler) rs -> byStatus.put(rs.getString("status"), rs.getLong("c")));

        Map<String, Long> lostByReason = new LinkedHashMap<>();
        jdbc.query("select coalesce(comment,'—') as reason, count(*) as c from product_request_history "
                        + "where to_status = 'LOST' group by comment",
                (RowCallbackHandler) rs -> lostByReason.put(rs.getString("reason"), rs.getLong("c")));

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long completed = byStatus.getOrDefault("COMPLETED", 0L);
        double completionRate = total == 0 ? 0.0 : (double) completed / total;
        return new ProductRequestMetrics(byStatus, lostByReason, total, completionRate);
    }
}
