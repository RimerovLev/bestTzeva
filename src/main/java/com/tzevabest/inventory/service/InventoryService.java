package com.tzevabest.inventory.service;

import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.inventory.dto.ReserveItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Резервирование остатков под заявку на товар. Резерв — явная запись {@code stock_reservations} с
 * TTL, плюс увеличение {@code inventory.reserved_qty} и движение {@code RESERVATION}. Считается только
 * по активным складам. Конкурентно-безопасно: занятие слота — атомарный условный UPDATE (гонку за
 * последними банками выигрывает ровно доступное число).
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final JdbcTemplate jdbc;

    @Value("${app.inventory.reservation-ttl:PT72H}")
    private Duration reservationTtl;

    /**
     * Зарезервировать все позиции заявки. Если хотя бы одну нельзя обеспечить целиком — вся операция
     * откатывается (ConflictException {@code inventory.insufficient_stock}).
     */
    @Transactional
    public void reserve(long requestId, List<ReserveItem> items) {
        Instant expiresAt = Instant.now().plus(reservationTtl);
        for (ReserveItem item : items) {
            reserveVariant(requestId, item.variantId(), item.qty(), expiresAt);
        }
    }

    private void reserveVariant(long requestId, long variantId, int qty, Instant expiresAt) {
        int remaining = qty;
        List<long[]> rows = jdbc.query(
                "select i.id, i.warehouse_id from inventory i join warehouses w on w.id = i.warehouse_id "
                        + "where i.variant_id = ? and w.is_active and (i.qty - i.reserved_qty) > 0 "
                        + "order by (i.qty - i.reserved_qty) desc",
                (rs, n) -> new long[]{rs.getLong("id"), rs.getLong("warehouse_id")}, variantId);

        for (long[] row : rows) {
            if (remaining <= 0) {
                break;
            }
            long inventoryId = row[0];
            long warehouseId = row[1];
            Integer available = jdbc.queryForObject(
                    "select qty - reserved_qty from inventory where id = ?", Integer.class, inventoryId);
            if (available == null || available <= 0) {
                continue;
            }
            int chunk = Math.min(available, remaining);
            // Атомарно: условие перепроверяется под блокировкой строки — гонку это отсекает.
            int updated = jdbc.update(
                    "update inventory set reserved_qty = reserved_qty + ?, version = version + 1 "
                            + "where id = ? and (qty - reserved_qty) >= ?", chunk, inventoryId, chunk);
            if (updated == 1) {
                int qtyAfter = jdbc.queryForObject("select qty from inventory where id = ?", Integer.class, inventoryId);
                jdbc.update("""
                        insert into stock_reservations (variant_id, warehouse_id, request_id, qty, status, expires_at)
                        values (?, ?, ?, ?, 'ACTIVE', ?)""",
                        variantId, warehouseId, requestId, chunk, java.sql.Timestamp.from(expiresAt));
                jdbc.update("""
                        insert into inventory_movements (variant_id, warehouse_id, type, qty_delta, qty_after, reference_type, reference_id)
                        values (?, ?, 'RESERVATION', ?, ?, 'REQUEST', ?)""",
                        variantId, warehouseId, -chunk, qtyAfter, requestId);
                remaining -= chunk;
            }
        }
        if (remaining > 0) {
            throw new ConflictException("inventory.insufficient_stock");
        }
    }

    /** Снять все активные резервы заявки (RELEASED). Возвращает qty назад в доступный остаток. */
    @Transactional
    public void release(long requestId) {
        releaseInternal(requestId, "RELEASED");
    }

    /** Снять просроченные резервы заявки (EXPIRED). */
    @Transactional
    public void releaseExpired(long requestId) {
        releaseInternal(requestId, "EXPIRED");
    }

    private void releaseInternal(long requestId, String finalStatus) {
        List<long[]> reservations = jdbc.query(
                "select id, variant_id, warehouse_id, qty from stock_reservations "
                        + "where request_id = ? and status = 'ACTIVE'",
                (rs, n) -> new long[]{rs.getLong("id"), rs.getLong("variant_id"),
                        rs.getLong("warehouse_id"), rs.getLong("qty")}, requestId);
        for (long[] r : reservations) {
            long resId = r[0];
            long variantId = r[1];
            long warehouseId = r[2];
            int qty = (int) r[3];
            jdbc.update("update inventory set reserved_qty = reserved_qty - ?, version = version + 1 "
                    + "where variant_id = ? and warehouse_id = ?", qty, variantId, warehouseId);
            jdbc.update("update stock_reservations set status = ?, updated_at = now() where id = ?",
                    finalStatus, resId);
            Integer qtyAfter = jdbc.queryForObject(
                    "select qty from inventory where variant_id = ? and warehouse_id = ?",
                    Integer.class, variantId, warehouseId);
            jdbc.update("""
                    insert into inventory_movements (variant_id, warehouse_id, type, qty_delta, qty_after, reference_type, reference_id)
                    values (?, ?, 'RESERVATION_RELEASE', ?, ?, 'REQUEST', ?)""",
                    variantId, warehouseId, qty, qtyAfter == null ? 0 : qtyAfter, requestId);
        }
    }

    /** Заявки, у которых есть просроченные активные резервы. */
    public List<Long> findRequestsWithExpiredReservations() {
        return jdbc.queryForList(
                "select distinct request_id from stock_reservations where status = 'ACTIVE' and expires_at < now()",
                Long.class);
    }
}
