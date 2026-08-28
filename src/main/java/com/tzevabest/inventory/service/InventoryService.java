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
 * Резервирование остатков под заявку на товар. Резерв — явная запись {@code stock_reservations} с TTL,
 * плюс увеличение {@code inventory.reserved_qty}. Считается только по активным складам.
 * <p>
 * Журнал {@code inventory_movements} сходится (R-41): резерв/снятие физический остаток не двигают
 * ({@code qty_delta = 0}), их дельта пишется в {@code reserved_delta}/{@code reserved_after}; продажа
 * (SALE) двигает и то, и другое. Поэтому {@code sum(qty_delta)} по варианту/складу равно
 * {@code inventory.qty}.
 * <p>
 * Снятие/списание резерва идемпотентно (R-42): статус меняется условным
 * {@code update ... where status = 'ACTIVE'}, и остаток корректируется только если запись реально
 * захвачена — повторный проход не вычтет второй раз. Конкуренция за остаток — атомарный условный
 * UPDATE {@code where (qty - reserved_qty) >= ?}.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final JdbcTemplate jdbc;

    @Value("${app.inventory.reservation-ttl:PT72H}")
    private Duration reservationTtl;

    // ── Приход (для сходимости журнала и наполнения остатка) ─────────────────────

    /** Приход товара: увеличивает остаток и пишет движение RECEIPT. Создаёт строку inventory при нужде. */
    @Transactional
    public void receive(long variantId, long warehouseId, int qty) {
        int updated = jdbc.update("update inventory set qty = qty + ?, version = version + 1 "
                + "where variant_id = ? and warehouse_id = ?", qty, variantId, warehouseId);
        if (updated == 0) {
            jdbc.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, 0)",
                    variantId, warehouseId, qty);
        }
        int[] st = state(variantId, warehouseId);
        jdbc.update("""
                insert into inventory_movements (variant_id, warehouse_id, type, qty_delta, qty_after,
                                                 reserved_delta, reserved_after, reference_type)
                values (?, ?, 'RECEIPT', ?, ?, 0, ?, 'MANUAL')""",
                variantId, warehouseId, qty, st[0], st[1]);
    }

    // ── Резервирование ───────────────────────────────────────────────────────────

    @Transactional
    public void reserve(long requestId, List<ReserveItem> items) {
        Instant expiresAt = Instant.now().plus(reservationTtl);
        // Детерминированный порядок захвата строк (R-45): по variant_id, иначе две заявки с
        // пересекающимися наборами могут взять блокировки в противоположном порядке и словить дедлок.
        items.stream()
                .sorted(java.util.Comparator.comparingLong(ReserveItem::variantId))
                .forEach(item -> reserveVariant(requestId, item.variantId(), item.qty(), expiresAt));
    }

    private void reserveVariant(long requestId, long variantId, int qty, Instant expiresAt) {
        int remaining = qty;
        // Порядок захвата строк — по inventory.id (стабилен), а не по доступному остатку (меняется):
        // иначе порядок блокировок недетерминирован и возможен дедлок (R-45).
        List<long[]> rows = jdbc.query(
                "select i.id, i.warehouse_id from inventory i join warehouses w on w.id = i.warehouse_id "
                        + "where i.variant_id = ? and w.is_active and (i.qty - i.reserved_qty) > 0 "
                        + "order by i.id",
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
            int updated = jdbc.update("update inventory set reserved_qty = reserved_qty + ?, version = version + 1 "
                    + "where id = ? and (qty - reserved_qty) >= ?", chunk, inventoryId, chunk);
            if (updated == 1) {
                int[] st = stateById(inventoryId);
                jdbc.update("""
                        insert into stock_reservations (variant_id, warehouse_id, request_id, qty, status, expires_at)
                        values (?, ?, ?, ?, 'ACTIVE', ?)""",
                        variantId, warehouseId, requestId, chunk, java.sql.Timestamp.from(expiresAt));
                movement(variantId, warehouseId, "RESERVATION", 0, st[0], chunk, st[1], requestId);
                remaining -= chunk;
            }
        }
        if (remaining > 0) {
            throw new ConflictException("inventory.insufficient_stock");
        }
    }

    // ── Снятие / списание ────────────────────────────────────────────────────────

    /** Снять активные резервы заявки (отмена/потеря). Идемпотентно. */
    @Transactional
    public void release(long requestId) {
        releaseInternal(requestId, "RELEASED", "request_cancelled");
    }

    /** Снять просроченные резервы заявки (истечение TTL). Идемпотентно. */
    @Transactional
    public void releaseExpired(long requestId) {
        releaseInternal(requestId, "EXPIRED", "expired");
    }

    private void releaseInternal(long requestId, String finalStatus, String reason) {
        for (long[] r : activeReservations(requestId)) {
            long resId = r[0];
            long variantId = r[1];
            long warehouseId = r[2];
            int qty = (int) r[3];
            // Захватываем запись условно: только победивший проход двигает остаток (R-42).
            int claimed = jdbc.update("update stock_reservations set status = ?, released_reason = ?, "
                    + "updated_at = now() where id = ? and status = 'ACTIVE'", finalStatus, reason, resId);
            if (claimed == 1) {
                jdbc.update("update inventory set reserved_qty = reserved_qty - ?, version = version + 1 "
                        + "where variant_id = ? and warehouse_id = ?", qty, variantId, warehouseId);
                int[] st = state(variantId, warehouseId);
                movement(variantId, warehouseId, "RESERVATION_RELEASE", 0, st[0], -qty, st[1], requestId);
            }
        }
    }

    /** Списать резервы заявки как продажу (COMPLETED): остаток и резерв уменьшаются, статус CONSUMED. */
    @Transactional
    public void consume(long requestId) {
        for (long[] r : activeReservations(requestId)) {
            long resId = r[0];
            long variantId = r[1];
            long warehouseId = r[2];
            int qty = (int) r[3];
            int claimed = jdbc.update("update stock_reservations set status = 'CONSUMED', released_reason = 'sold', "
                    + "updated_at = now() where id = ? and status = 'ACTIVE'", resId);
            if (claimed == 1) {
                jdbc.update("update inventory set qty = qty - ?, reserved_qty = reserved_qty - ?, version = version + 1 "
                        + "where variant_id = ? and warehouse_id = ?", qty, qty, variantId, warehouseId);
                int[] st = state(variantId, warehouseId);
                movement(variantId, warehouseId, "SALE", -qty, st[0], -qty, st[1], requestId);
            }
        }
    }

    /** Заявки, у которых есть просроченные активные резервы (для фоновой уборки). */
    public List<Long> findRequestsWithExpiredReservations(int limit) {
        return jdbc.queryForList(
                "select distinct request_id from stock_reservations where status = 'ACTIVE' and expires_at < now() "
                        + "limit ?", Long.class, limit);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private List<long[]> activeReservations(long requestId) {
        return jdbc.query("select id, variant_id, warehouse_id, qty from stock_reservations "
                        + "where request_id = ? and status = 'ACTIVE'",
                (rs, n) -> new long[]{rs.getLong("id"), rs.getLong("variant_id"),
                        rs.getLong("warehouse_id"), rs.getLong("qty")}, requestId);
    }

    private void movement(long variantId, long warehouseId, String type, int qtyDelta, int qtyAfter,
                          int reservedDelta, int reservedAfter, long requestId) {
        jdbc.update("""
                insert into inventory_movements (variant_id, warehouse_id, type, qty_delta, qty_after,
                                                 reserved_delta, reserved_after, reference_type, reference_id)
                values (?, ?, ?, ?, ?, ?, ?, 'REQUEST', ?)""",
                variantId, warehouseId, type, qtyDelta, qtyAfter, reservedDelta, reservedAfter, requestId);
    }

    /** {qty, reserved_qty} по варианту/складу. */
    private int[] state(long variantId, long warehouseId) {
        return jdbc.queryForObject("select qty, reserved_qty from inventory where variant_id = ? and warehouse_id = ?",
                (rs, n) -> new int[]{rs.getInt("qty"), rs.getInt("reserved_qty")}, variantId, warehouseId);
    }

    private int[] stateById(long inventoryId) {
        return jdbc.queryForObject("select qty, reserved_qty from inventory where id = ?",
                (rs, n) -> new int[]{rs.getInt("qty"), rs.getInt("reserved_qty")}, inventoryId);
    }
}
