package com.tzevabest.inventory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.admin.service.AuditService;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.inventory.dto.WarehouseCreateRequest;
import com.tzevabest.inventory.dto.WarehouseResponse;
import com.tzevabest.inventory.dto.WarehouseUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * CRUD складов. Деактивация запрещена, пока на складе есть ненулевой остаток или активные резервы —
 * иначе резерв/остаток «повис» бы на неактивном складе, невидимом для резервирования. Все изменения —
 * в audit_log.
 */
@Service
@RequiredArgsConstructor
public class WarehouseAdminService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final ObjectMapper json;

    public List<WarehouseResponse> list() {
        return jdbc.query("select id, code, name_translations, address, is_pickup_point, is_active, sort_order "
                + "from warehouses order by sort_order, code", this::map);
    }

    public WarehouseResponse get(long id) {
        try {
            return jdbc.queryForObject("select id, code, name_translations, address, is_pickup_point, "
                    + "is_active, sort_order from warehouses where id = ?", this::map, id);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("warehouse.not_found");
        }
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest req) {
        if (codeExists(req.code(), null)) {
            throw new ConflictException("warehouse.code_taken", req.code());
        }
        Long id = jdbc.queryForObject("""
                        insert into warehouses (code, name_translations, address, is_pickup_point, sort_order)
                        values (?, cast(? as jsonb), ?, ?, ?) returning id""",
                Long.class, req.code(), toJson(req.nameTranslations()), req.address(),
                Boolean.TRUE.equals(req.pickupPoint()), req.sortOrder() == null ? 0 : req.sortOrder());
        audit("warehouse.create", id, Map.of("code", req.code()));
        return get(id);
    }

    @Transactional
    public WarehouseResponse update(long id, WarehouseUpdateRequest req) {
        get(id); // 404, если нет
        if (codeExists(req.code(), id)) {
            throw new ConflictException("warehouse.code_taken", req.code());
        }
        jdbc.update("""
                        update warehouses set code = ?, name_translations = cast(? as jsonb), address = ?,
                                              is_pickup_point = ?, sort_order = ?, updated_at = now()
                        where id = ?""",
                req.code(), toJson(req.nameTranslations()), req.address(),
                Boolean.TRUE.equals(req.pickupPoint()), req.sortOrder() == null ? 0 : req.sortOrder(), id);
        audit("warehouse.update", id, Map.of("code", req.code()));
        return get(id);
    }

    @Transactional
    public WarehouseResponse setActive(long id, boolean active) {
        get(id); // 404, если нет
        if (!active) {
            requireEmptyBeforeDeactivation(id);
        }
        jdbc.update("update warehouses set is_active = ?, updated_at = now() where id = ?", active, id);
        audit(active ? "warehouse.activate" : "warehouse.deactivate", id, Map.of("active", active));
        return get(id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void requireEmptyBeforeDeactivation(long id) {
        // Активные резервы проверяем первыми: резерв всегда подразумевает qty > 0, но сообщение
        // «есть активные резервы» точнее для оператора, чем общее «есть остаток».
        Integer reservations = jdbc.queryForObject(
                "select count(*) from stock_reservations where warehouse_id = ? and status = 'ACTIVE'",
                Integer.class, id);
        if (reservations != null && reservations > 0) {
            throw new ConflictException("warehouse.has_active_reservations");
        }
        Integer stock = jdbc.queryForObject(
                "select count(*) from inventory where warehouse_id = ? and qty > 0", Integer.class, id);
        if (stock != null && stock > 0) {
            throw new ConflictException("warehouse.has_stock");
        }
    }

    private boolean codeExists(String code, Long exceptId) {
        Integer c = (exceptId == null)
                ? jdbc.queryForObject("select count(*) from warehouses where code = ?", Integer.class, code)
                : jdbc.queryForObject("select count(*) from warehouses where code = ? and id <> ?",
                        Integer.class, code, exceptId);
        return c != null && c > 0;
    }

    private WarehouseResponse map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WarehouseResponse(rs.getLong("id"), rs.getString("code"),
                fromJson(rs.getString("name_translations")), rs.getString("address"),
                rs.getBoolean("is_pickup_point"), rs.getBoolean("is_active"), rs.getInt("sort_order"));
    }

    private String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid name_translations", e);
        }
    }

    private Map<String, String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return json.readValue(value, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private void audit(String action, Long id, Map<String, Object> changes) {
        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                action, "warehouse", id, changes);
    }
}
