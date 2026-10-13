package com.tzevabest.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.admin.dto.AuditEntryView;
import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.web.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Чтение журнала аудита (T-13.3). */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private static final ZoneId TZ = ZoneId.of("Asia/Jerusalem");

    /** Имя автора — тем же правилом, что в списке исполнителей: «Имя Фамилия», иначе email. */
    private static final String SELECT = "select a.id, a.actor_user_id, a.actor_ip, a.action, a.entity_type, "
            + "a.entity_id, a.changes, a.created_at, "
            + "coalesce(nullif(trim(concat_ws(' ', u.first_name, u.last_name)), ''), u.email) as actor_name "
            + "from audit_log a left join users u on u.id = a.actor_user_id";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PageResponse<AuditEntryView> list(Long actorUserId, String entityType, Long entityId,
                                             String action, LocalDate from, LocalDate to, PageQuery page) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (actorUserId != null) {
            where.append(" and a.actor_user_id = ?");
            args.add(actorUserId);
        }
        if (entityType != null) {
            where.append(" and a.entity_type = ?");
            args.add(entityType);
        }
        if (entityId != null) {
            where.append(" and a.entity_id = ?");
            args.add(entityId);
        }
        if (action != null) {
            where.append(" and a.action = ?");
            args.add(action);
        }
        if (from != null) {
            where.append(" and a.created_at >= ?");
            args.add(java.sql.Timestamp.from(from.atStartOfDay(TZ).toInstant()));
        }
        if (to != null) {
            where.append(" and a.created_at < ?");
            args.add(java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(TZ).toInstant()));
        }

        Long total = jdbc.queryForObject("select count(*) from audit_log a" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page.size());
        pageArgs.add((long) page.page() * page.size());
        List<AuditEntryView> items = jdbc.query(SELECT + where
                        + " order by a.created_at desc, a.id desc limit ? offset ?",
                (rs, n) -> map(rs), pageArgs.toArray());
        return PageResponse.of(items, page.page(), page.size(), total == null ? 0 : total);
    }

    public List<AuditEntryView> entityHistory(String type, long id) {
        return jdbc.query(SELECT + " where a.entity_type = ? and a.entity_id = ? order by a.created_at desc, a.id desc",
                (rs, n) -> map(rs), type, id);
    }

    private AuditEntryView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AuditEntryView(rs.getLong("id"), (Long) rs.getObject("actor_user_id"),
                rs.getString("actor_name"), rs.getString("actor_ip"),
                rs.getString("action"), rs.getString("entity_type"), (Long) rs.getObject("entity_id"),
                parse(rs.getString("changes")), rs.getTimestamp("created_at").toInstant());
    }

    private Map<String, Object> parse(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(jsonStr, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
