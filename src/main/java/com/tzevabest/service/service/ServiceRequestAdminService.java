package com.tzevabest.service.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.dto.SortRequest;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.media.service.MediaStorage;
import com.tzevabest.notification.dto.MeasurementReminderModel;
import com.tzevabest.notification.dto.NewRequestAdminModel;
import com.tzevabest.notification.dto.NotificationTemplate;
import com.tzevabest.notification.dto.ServiceRequestQuotedModel;
import com.tzevabest.notification.service.NotificationService;
import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.ServiceMediaType;
import com.tzevabest.service.domain.ServiceRequestStatus;
import com.tzevabest.service.domain.WorkType;
import com.tzevabest.service.dto.CommentView;
import com.tzevabest.service.dto.HistoryView;
import com.tzevabest.service.dto.QuoteRequest;
import com.tzevabest.service.dto.ServiceRequestDetail;
import com.tzevabest.service.dto.ServiceRequestListItem;
import com.tzevabest.service.dto.ServiceRequestMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Воронка заявок на покраску в админке (T-11.3): список с фильтрами, карточка, машина состояний,
 * назначение менеджера (с автораспределением за флагом), внутренние комментарии, планирование замера
 * с отложенным напоминанием, выставление котировки с PDF. Устройство — по образцу
 * ProductRequestAdminService.
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestAdminService {

    /** Разрешённые переходы (docs/service-request-states.md). Терминальные исходящих не имеют. */
    private static final Map<ServiceRequestStatus, Set<ServiceRequestStatus>> ALLOWED =
            new EnumMap<>(ServiceRequestStatus.class);

    static {
        ALLOWED.put(ServiceRequestStatus.NEW, EnumSet.of(ServiceRequestStatus.CONTACTED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.CONTACTED, EnumSet.of(ServiceRequestStatus.MEASUREMENT_SCHEDULED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.MEASUREMENT_SCHEDULED, EnumSet.of(ServiceRequestStatus.MEASURED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.MEASURED, EnumSet.of(ServiceRequestStatus.QUOTED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.QUOTED, EnumSet.of(ServiceRequestStatus.ACCEPTED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.ACCEPTED, EnumSet.of(ServiceRequestStatus.IN_PROGRESS, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.IN_PROGRESS, EnumSet.of(ServiceRequestStatus.COMPLETED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.COMPLETED, EnumSet.noneOf(ServiceRequestStatus.class));
        ALLOWED.put(ServiceRequestStatus.REJECTED, EnumSet.noneOf(ServiceRequestStatus.class));
        ALLOWED.put(ServiceRequestStatus.LOST, EnumSet.noneOf(ServiceRequestStatus.class));
    }

    private static final Set<String> SORTABLE = Set.of("created_at", "status", "request_number", "area_sqm");
    private static final DateTimeFormatter REMINDER_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Jerusalem"));

    private final NotificationService notifications;
    private final QuotePdfGenerator quotePdf;
    private final MediaStorage mediaStorage;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final JdbcTemplate jdbc;

    @Value("${app.service.auto-assign-enabled:false}")
    private boolean autoAssignEnabled;

    // ── Список ────────────────────────────────────────────────────────────────────

    public PageResponse<ServiceRequestListItem> list(ServiceRequestStatus status, PropertyType propertyType,
                                                     String city, Long managerId, java.time.LocalDate from,
                                                     java.time.LocalDate to, BigDecimal areaMin, BigDecimal areaMax,
                                                     String source, String q, PageQuery page) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" and status = ?");
            args.add(status.name());
        }
        if (propertyType != null) {
            where.append(" and property_type = ?");
            args.add(propertyType.name());
        }
        if (city != null && !city.isBlank()) {
            where.append(" and city ilike ?");
            args.add("%" + city.trim() + "%");
        }
        if (managerId != null) {
            where.append(" and assigned_manager_id = ?");
            args.add(managerId);
        }
        if (from != null) {
            where.append(" and created_at >= ?");
            args.add(java.sql.Timestamp.from(from.atStartOfDay(ZoneId.of("Asia/Jerusalem")).toInstant()));
        }
        if (to != null) {
            where.append(" and created_at < ?");
            args.add(java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Jerusalem")).toInstant()));
        }
        if (areaMin != null) {
            where.append(" and area_sqm >= ?");
            args.add(areaMin);
        }
        if (areaMax != null) {
            where.append(" and area_sqm <= ?");
            args.add(areaMax);
        }
        if (source != null && !source.isBlank()) {
            where.append(" and source = ?");
            args.add(source.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (request_number ilike ? or customer_phone ilike ? or customer_name ilike ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        Long total = jdbc.queryForObject("select count(*) from service_requests" + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page.size());
        pageArgs.add((long) page.page() * page.size());
        List<ServiceRequestListItem> items = jdbc.query("""
                select id, request_number, customer_name, customer_phone, property_type, city, area_sqm,
                       status, assigned_manager_id, source, created_at
                from service_requests""" + where + orderBy(page.sort()) + " limit ? offset ?",
                (rs, n) -> new ServiceRequestListItem(rs.getLong("id"), rs.getString("request_number"),
                        rs.getString("customer_name"), rs.getString("customer_phone"),
                        PropertyType.valueOf(rs.getString("property_type")), rs.getString("city"),
                        rs.getBigDecimal("area_sqm"), ServiceRequestStatus.valueOf(rs.getString("status")),
                        (Long) rs.getObject("assigned_manager_id"), rs.getString("source"),
                        rs.getTimestamp("created_at").toInstant()),
                pageArgs.toArray());

        return PageResponse.of(items, page.page(), page.size(), totalElements);
    }

    public ServiceRequestMetrics metrics() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long[] total = {0};
        jdbc.query("select status, count(*) c from service_requests group by status",
                (RowCallbackHandler) rs -> {
                    byStatus.put(rs.getString("status"), rs.getLong("c"));
                    total[0] += rs.getLong("c");
                });
        return new ServiceRequestMetrics(total[0], byStatus);
    }

    // ── Карточка ──────────────────────────────────────────────────────────────────

    public ServiceRequestDetail detail(long id) {
        ServiceRequestDetail base = loadDetail(id);
        List<HistoryView> history = jdbc.query("""
                select from_status, to_status, changed_by, comment, created_at
                from service_request_history where request_id = ? order by created_at, id""",
                (rs, n) -> new HistoryView(rs.getString("from_status"), rs.getString("to_status"),
                        (Long) rs.getObject("changed_by"), rs.getString("comment"),
                        rs.getTimestamp("created_at").toInstant()), id);
        List<CommentView> comments = jdbc.query("""
                select id, author_id, body, created_at from service_request_comments
                where request_id = ? order by created_at, id""",
                (rs, n) -> new CommentView(rs.getLong("id"), (Long) rs.getObject("author_id"),
                        rs.getString("body"), rs.getTimestamp("created_at").toInstant()), id);
        return withChildren(base, history, comments);
    }

    /** Ключ медиа клиента в приватном бакете — подписывается в ссылку на уровне контроллера. */
    public record MediaKey(long id, ServiceMediaType type, String objectKey, Instant uploadedAt) {
    }

    /**
     * Медиа клиента по заявке (фото помещения). Просмотр — доступ к персональным данным, поэтому
     * пишется в журнал аудита. Записи без object_key (незавершённая загрузка) не отдаются.
     */
    public List<MediaKey> mediaKeys(long id) {
        currentStatus(id); // 404 на несуществующую заявку
        List<MediaKey> keys = jdbc.query("""
                select id, type, object_key, uploaded_at from service_request_media
                where request_id = ? and object_key is not null order by uploaded_at, id""",
                (rs, n) -> new MediaKey(rs.getLong("id"), ServiceMediaType.valueOf(rs.getString("type")),
                        rs.getString("object_key"), rs.getTimestamp("uploaded_at").toInstant()), id);
        audit("service_request.media_view", id, Map.of("count", keys.size()));
        return keys;
    }

    // ── Машина состояний ─────────────────────────────────────────────────────────

    @Transactional
    public ServiceRequestDetail transition(long id, ServiceRequestStatus to, String comment) {
        ServiceRequestStatus from = currentStatus(id);
        requireAllowed(from, to);
        applyStatus(id, from, to, comment);
        audit("service_request.transition", id, Map.of("from", from.name(), "to", to.name()));
        return detail(id);
    }

    @Transactional
    public ServiceRequestDetail assign(long id, Long managerId) {
        requireExists(id);
        jdbc.update("update service_requests set assigned_manager_id = ?, updated_at = now() where id = ?",
                managerId, id);
        if (managerId != null) {
            notifyAssignee(id, managerId);
        }
        audit("service_request.assign", id, Map.of("manager_id", managerId == null ? "null" : managerId));
        return detail(id);
    }

    @Transactional
    public ServiceRequestDetail autoAssign(long id) {
        if (!autoAssignEnabled) {
            throw new ConflictException("service_request.auto_assign_disabled");
        }
        Long manager = jdbc.query("""
                select u.id from users u
                left join service_requests s on s.assigned_manager_id = u.id
                    and s.status not in ('COMPLETED','REJECTED','LOST')
                where u.role = 'MANAGER' and u.status = 'ACTIVE'
                group by u.id order by count(s.id), u.id limit 1""",
                rs -> rs.next() ? rs.getLong("id") : null);
        if (manager == null) {
            throw new ConflictException("service_request.no_manager");
        }
        return assign(id, manager);
    }

    @Transactional
    public void addComment(long id, String text) {
        requireExists(id);
        jdbc.update("insert into service_request_comments (request_id, author_id, body) values (?, ?, ?)",
                id, actorId(), text);
    }

    @Transactional
    public ServiceRequestDetail scheduleMeasurement(long id, Instant at) {
        ServiceRequestStatus from = currentStatus(id);
        requireAllowed(from, ServiceRequestStatus.MEASUREMENT_SCHEDULED);
        jdbc.update("update service_requests set measurement_at = ? where id = ?", java.sql.Timestamp.from(at), id);
        applyStatus(id, from, ServiceRequestStatus.MEASUREMENT_SCHEDULED, "measurement scheduled");

        // Отложенное напоминание клиенту за сутки (через очередь, не собственный планировщик).
        Instant remindAt = at.minus(Duration.ofDays(1));
        String phone = jdbc.queryForObject("select customer_phone from service_requests where id = ?", String.class, id);
        String number = jdbc.queryForObject("select request_number from service_requests where id = ?", String.class, id);
        String locale = jdbc.queryForObject("select locale from service_requests where id = ?", String.class, id);
        notifications.enqueueAt(NotificationTemplate.MEASUREMENT_REMINDER, phone, locale,
                new MeasurementReminderModel(number, REMINDER_FMT.format(at)),
                "measurement:" + id, remindAt);
        audit("service_request.measurement", id, Map.of("at", at.toString()));
        return detail(id);
    }

    @Transactional
    public ServiceRequestDetail quote(long id, QuoteRequest req) {
        ServiceRequestStatus from = currentStatus(id);
        requireAllowed(from, ServiceRequestStatus.QUOTED);
        Map<String, Object> row = jdbc.queryForMap(
                "select request_number, customer_name, customer_email, locale from service_requests where id = ?", id);

        byte[] pdf = quotePdf.generate(new QuotePdfGenerator.QuoteData(
                (String) row.get("request_number"), (String) row.get("customer_name"),
                req.price(), req.workComposition(), req.termDays(), req.comment()));
        String pdfKey = mediaStorage.putPrivate("quotes/" + id, "quote.pdf", "application/pdf", pdf);

        jdbc.update("update service_requests set quoted_price = ?, quote_pdf_key = ? where id = ?",
                req.price(), pdfKey, id);
        applyStatus(id, from, ServiceRequestStatus.QUOTED, "quote issued");

        String email = (String) row.get("customer_email");
        if (email != null && !email.isBlank()) {
            notifications.enqueue(NotificationTemplate.SERVICE_REQUEST_QUOTED, email, (String) row.get("locale"),
                    new ServiceRequestQuotedModel((String) row.get("request_number"), req.price().toPlainString()),
                    "quote:" + id);
        }
        audit("service_request.quote", id, Map.of("price", req.price()));
        return detail(id);
    }

    /** Presigned-ссылка на PDF КП для персонала. */
    public String quotePdfKey(long id) {
        String key = jdbc.queryForObject("select quote_pdf_key from service_requests where id = ?", String.class, id);
        if (key == null) {
            throw new NotFoundException("service_request.quote_missing");
        }
        return key;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void applyStatus(long id, ServiceRequestStatus from, ServiceRequestStatus to, String comment) {
        jdbc.update("update service_requests set status = ?, updated_at = now() where id = ?", to.name(), id);
        jdbc.update("""
                insert into service_request_history (request_id, from_status, to_status, changed_by, comment)
                values (?, ?, ?, ?, ?)""", id, from.name(), to.name(), actorId(), comment);
    }

    private void notifyAssignee(long id, long managerId) {
        String email = jdbc.query("select email from users where id = ?",
                rs -> rs.next() ? rs.getString("email") : null, managerId);
        String number = jdbc.queryForObject("select request_number from service_requests where id = ?", String.class, id);
        if (email != null && !email.isBlank()) {
            notifications.enqueue(NotificationTemplate.NEW_REQUEST_ADMIN, email, "he",
                    new NewRequestAdminModel(number, "painting"), "assign:" + id + ":" + managerId);
        }
    }

    private ServiceRequestStatus currentStatus(long id) {
        String s = jdbc.query("select status from service_requests where id = ?",
                rs -> rs.next() ? rs.getString("status") : null, id);
        if (s == null) {
            throw new NotFoundException("service_request.not_found");
        }
        return ServiceRequestStatus.valueOf(s);
    }

    private void requireAllowed(ServiceRequestStatus from, ServiceRequestStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new ConflictException("service_request.transition_forbidden", from.name(), to.name());
        }
    }

    private void requireExists(long id) {
        Integer c = jdbc.queryForObject("select count(*) from service_requests where id = ?", Integer.class, id);
        if (c == null || c == 0) {
            throw new NotFoundException("service_request.not_found");
        }
    }

    private ServiceRequestDetail loadDetail(long id) {
        try {
            return jdbc.queryForObject("""
                    select id, request_number, customer_name, customer_phone, customer_email, property_type,
                           area_sqm, rooms_count, ceiling_height_m, city, address, preferred_date_from,
                           preferred_date_to, comment, estimated_price_min, estimated_price_max, quoted_price,
                           status, assigned_manager_id, source, locale, measurement_at, created_at
                    from service_requests where id = ?""",
                    (rs, n) -> new ServiceRequestDetail(rs.getLong("id"), rs.getString("request_number"),
                            rs.getString("customer_name"), rs.getString("customer_phone"), rs.getString("customer_email"),
                            PropertyType.valueOf(rs.getString("property_type")), rs.getBigDecimal("area_sqm"),
                            (Integer) rs.getObject("rooms_count"), rs.getBigDecimal("ceiling_height_m"),
                            loadWorkTypes(id), rs.getString("city"), rs.getString("address"),
                            rs.getObject("preferred_date_from", java.time.LocalDate.class),
                            rs.getObject("preferred_date_to", java.time.LocalDate.class), rs.getString("comment"),
                            rs.getBigDecimal("estimated_price_min"), rs.getBigDecimal("estimated_price_max"),
                            rs.getBigDecimal("quoted_price"), ServiceRequestStatus.valueOf(rs.getString("status")),
                            (Long) rs.getObject("assigned_manager_id"), rs.getString("source"), rs.getString("locale"),
                            ts(rs.getTimestamp("measurement_at")), rs.getTimestamp("created_at").toInstant(),
                            List.of(), List.of()), id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new NotFoundException("service_request.not_found");
        }
    }

    private Set<WorkType> loadWorkTypes(long id) {
        List<WorkType> list = jdbc.query("select work_type from service_request_work_types where request_id = ?",
                (rs, n) -> WorkType.valueOf(rs.getString("work_type")), id);
        return list.isEmpty() ? EnumSet.noneOf(WorkType.class) : EnumSet.copyOf(list);
    }

    private static ServiceRequestDetail withChildren(ServiceRequestDetail d, List<HistoryView> history,
                                                     List<CommentView> comments) {
        return new ServiceRequestDetail(d.id(), d.requestNumber(), d.customerName(), d.customerPhone(),
                d.customerEmail(), d.propertyType(), d.areaSqm(), d.roomsCount(), d.ceilingHeightM(), d.workTypes(),
                d.city(), d.address(), d.preferredDateFrom(), d.preferredDateTo(), d.comment(), d.estimatedPriceMin(),
                d.estimatedPriceMax(), d.quotedPrice(), d.status(), d.assignedManagerId(), d.source(), d.locale(),
                d.measurementAt(), d.createdAt(), history, comments);
    }

    private static Instant ts(java.sql.Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private String orderBy(SortRequest sort) {
        List<String> parts = new ArrayList<>();
        for (SortRequest.Order o : sort.orders()) {
            if (SORTABLE.contains(o.property())) {
                parts.add(o.property() + (o.direction() == SortRequest.Direction.DESC ? " desc" : " asc"));
            }
        }
        return parts.isEmpty() ? " order by created_at desc" : " order by " + String.join(", ", parts);
    }

    private Long actorId() {
        return currentUser.optional().map(p -> p.userId()).orElse(null);
    }

    private void audit(String action, long id, Map<String, Object> changes) {
        audit.record(actorId(), currentUser.ip(), action, "service_request", id, changes);
    }
}
