package com.tzevabest.inventory.service;

import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.inventory.domain.MovementType;
import com.tzevabest.inventory.domain.ReferenceType;
import com.tzevabest.inventory.dto.MovementContext;
import com.tzevabest.inventory.dto.ReserveItem;
import com.tzevabest.inventory.event.LowStockEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Складской учёт: единственная точка изменения таблицы {@code inventory} (прямые UPDATE из других
 * модулей запрещены, см. ArchUnit-правило). Приход, резервирование под заявку, а также движения
 * остатка — списание, инвентаризация, перемещение между складами — плюс публичное чтение доступного
 * количества для каталога.
 * <p>
 * Резерв — явная запись {@code stock_reservations} с TTL, плюс увеличение
 * {@code inventory.reserved_qty}. Считается только по активным складам.
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
    private final ApplicationEventPublisher events;

    @Value("${app.inventory.reservation-ttl:PT72H}")
    private Duration reservationTtl;

    // ── Приход (для сходимости журнала и наполнения остатка) ─────────────────────

    /** Приход товара: увеличивает остаток и пишет движение RECEIPT. Создаёт строку inventory при нужде. */
    @Transactional
    public void receive(long variantId, long warehouseId, int qty) {
        receive(variantId, warehouseId, qty, MovementContext.manual(null), null);
    }

    /** Приход с контекстом (кто/по какому поводу) и комментарием — для админского массового прихода. */
    @Transactional
    public void receive(long variantId, long warehouseId, int qty, MovementContext ctx, String comment) {
        requirePositive(qty);
        // Один upsert вместо «update, иначе insert» (R-65): две одновременные операции на пустой склад
        // не поймают уникальное нарушение — прибавка сливается.
        jdbc.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, 0) "
                + "on conflict (variant_id, warehouse_id) do update set qty = inventory.qty + excluded.qty, "
                + "version = inventory.version + 1", variantId, warehouseId, qty);
        InvRow after = requireRow(variantId, warehouseId);
        recordMovement(variantId, warehouseId, MovementType.RECEIPT, qty, after.qty(), 0, after.reserved(),
                ctx, comment);
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

    // ── Публичное чтение доступного (для каталога и других модулей) ───────────────

    /** Число позиций ниже порога остатка — для дашборда (прямой SQL к inventory только здесь). */
    public long countLowStock() {
        Long count = jdbc.queryForObject(
                "select count(*) from inventory where low_stock_threshold is not null "
                        + "and (qty - reserved_qty) <= low_stock_threshold", Long.class);
        return count == null ? 0 : count;
    }

    /** Доступно к продаже по варианту суммарно по всем складам ({@code sum(qty - reserved_qty)}). */
    public int getAvailable(long variantId) {
        Integer available = jdbc.queryForObject(
                "select coalesce(sum(qty - reserved_qty), 0)::int from inventory where variant_id = ?",
                Integer.class, variantId);
        return available == null ? 0 : available;
    }

    /** Доступно по варианту в разрезе складов: {@code warehouse_id -> available}. */
    public Map<Long, Integer> getAvailableByWarehouse(long variantId) {
        Map<Long, Integer> byWarehouse = new LinkedHashMap<>();
        jdbc.query("select warehouse_id, (qty - reserved_qty) as available from inventory "
                        + "where variant_id = ? order by warehouse_id",
                (RowCallbackHandler) rs -> byWarehouse.put(rs.getLong("warehouse_id"), rs.getInt("available")),
                variantId);
        return byWarehouse;
    }

    /**
     * Доступно по списку вариантов ОДНИМ запросом (вызывается из листинга каталога, где вариантов
     * сотни). Варианты без строк inventory возвращаются с 0.
     */
    public Map<Long, Integer> getAvailableBatch(List<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(variantIds.size(), "?"));
        Map<Long, Integer> byVariant = new HashMap<>();
        jdbc.query("select variant_id, coalesce(sum(qty - reserved_qty), 0)::int as available from inventory "
                        + "where variant_id in (" + placeholders + ") group by variant_id",
                (RowCallbackHandler) rs -> byVariant.put(rs.getLong("variant_id"), rs.getInt("available")),
                variantIds.toArray());
        for (Long id : variantIds) {
            byVariant.putIfAbsent(id, 0);
        }
        return byVariant;
    }

    /** Хватает ли доступного остатка по варианту (суммарно по складам) на {@code qty} единиц. */
    public boolean isAvailable(long variantId, int qty) {
        return getAvailable(variantId) >= qty;
    }

    // ── Движения физического остатка (списание / инвентаризация / перемещение) ────

    /**
     * Списание с обязательной причиной. Списывается только доступное (резерв под заявки не трогается):
     * условный UPDATE {@code where (qty - reserved_qty) >= ?}. Пишет движение WRITE_OFF (qty_delta < 0).
     */
    @Transactional
    public void writeOff(long variantId, long warehouseId, int qty, String reason, MovementContext ctx) {
        requirePositive(qty);
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("inventory.writeoff_reason_required");
        }
        int updated = jdbc.update("update inventory set qty = qty - ?, version = version + 1 "
                        + "where variant_id = ? and warehouse_id = ? and (qty - reserved_qty) >= ?",
                qty, variantId, warehouseId, qty);
        if (updated == 0) {
            throw new ConflictException("inventory.insufficient_stock");
        }
        InvRow after = requireRow(variantId, warehouseId);
        recordMovement(variantId, warehouseId, MovementType.WRITE_OFF, -qty, after.qty(), 0, after.reserved(),
                ctx, reason);
        publishLowStockIfNeeded(variantId, warehouseId, after);
    }

    /**
     * Инвентаризация: выставляет АБСОЛЮТНОЕ значение qty, в журнал пишется дельта как разница с
     * прежним qty. Нельзя опустить qty ниже reserved_qty (нарушило бы check и оставило резерв без
     * покрытия) — иначе отказ с указанием количества в резерве.
     */
    @Transactional
    public void stocktake(long variantId, long warehouseId, int countedQty, MovementContext ctx) {
        if (countedQty < 0) {
            throw new ValidationException("inventory.qty_negative");
        }
        InvRow before = findRow(variantId, warehouseId);
        int oldQty = before == null ? 0 : before.qty();
        int reserved = before == null ? 0 : before.reserved();
        if (countedQty < reserved) {
            throw new ConflictException("inventory.stocktake_below_reserved", reserved);
        }
        // Инвентаризация присваивает АБСОЛЮТНОЕ значение, поэтому конфликтная ветка тоже присваивает
        // (excluded.qty), а не прибавляет (R-65): гонка на пустой склад не даст уникального нарушения.
        jdbc.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, 0) "
                + "on conflict (variant_id, warehouse_id) do update set qty = excluded.qty, "
                + "version = inventory.version + 1", variantId, warehouseId, countedQty);
        InvRow after = requireRow(variantId, warehouseId);
        recordMovement(variantId, warehouseId, MovementType.STOCKTAKE, countedQty - oldQty, after.qty(), 0,
                after.reserved(), ctx, null);
        publishLowStockIfNeeded(variantId, warehouseId, after);
    }

    /**
     * Перемещение между складами: два движения (TRANSFER_OUT со склада-источника, TRANSFER_IN на
     * склад-получатель) в одной транзакции. Со склада-источника уходит только доступное (резерв
     * остаётся на нём). Строки блокируются в порядке warehouse_id (R-45), чтобы встречные переносы
     * не поймали дедлок.
     */
    @Transactional
    public void transfer(long variantId, long fromWarehouseId, long toWarehouseId, int qty, MovementContext ctx) {
        requirePositive(qty);
        if (fromWarehouseId == toWarehouseId) {
            throw new ValidationException("inventory.transfer_same_warehouse");
        }
        // Детерминированный порядок блокировок существующих строк обоих складов — по warehouse_id
        // (R-45, R-68). Для встречного переноса (from/to меняются местами) множество строк то же самое,
        // а порядок захвата один и тот же, поэтому дедлока нет; сортировки по id для этого не требуется.
        jdbc.queryForList("select id from inventory where variant_id = ? and warehouse_id in (?, ?) "
                        + "order by warehouse_id for update",
                Long.class, variantId, fromWarehouseId, toWarehouseId);

        int out = jdbc.update("update inventory set qty = qty - ?, version = version + 1 "
                        + "where variant_id = ? and warehouse_id = ? and (qty - reserved_qty) >= ?",
                qty, variantId, fromWarehouseId, qty);
        if (out == 0) {
            throw new ConflictException("inventory.insufficient_stock");
        }
        InvRow fromRow = requireRow(variantId, fromWarehouseId);
        recordMovement(variantId, fromWarehouseId, MovementType.TRANSFER_OUT, -qty, fromRow.qty(), 0,
                fromRow.reserved(), ctx, null);

        // Приём на склад-получатель — upsert (R-65): если строки ещё нет, две одновременные приёмки
        // на один пустой склад сливаются, а не падают на уникальном индексе с невнятным 409.
        jdbc.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, 0) "
                + "on conflict (variant_id, warehouse_id) do update set qty = inventory.qty + excluded.qty, "
                + "version = inventory.version + 1", variantId, toWarehouseId, qty);
        InvRow toRow = requireRow(variantId, toWarehouseId);
        recordMovement(variantId, toWarehouseId, MovementType.TRANSFER_IN, qty, toRow.qty(), 0,
                toRow.reserved(), ctx, null);

        publishLowStockIfNeeded(variantId, fromWarehouseId, fromRow); // упасть ниже порога мог только склад-источник
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static void requirePositive(int qty) {
        if (qty <= 0) {
            throw new ValidationException("inventory.qty_positive");
        }
    }

    /** Строка остатка (после операции) для журнала и сигнала о низком остатке. */
    private record InvRow(long id, int qty, int reserved, Integer lowThreshold) {
    }

    private InvRow findRow(long variantId, long warehouseId) {
        List<InvRow> rows = jdbc.query(
                "select id, qty, reserved_qty, low_stock_threshold from inventory "
                        + "where variant_id = ? and warehouse_id = ?",
                (rs, n) -> new InvRow(rs.getLong("id"), rs.getInt("qty"), rs.getInt("reserved_qty"),
                        (Integer) rs.getObject("low_stock_threshold")),
                variantId, warehouseId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private InvRow requireRow(long variantId, long warehouseId) {
        InvRow row = findRow(variantId, warehouseId);
        if (row == null) {
            throw new ConflictException("inventory.insufficient_stock");
        }
        return row;
    }

    /** Движение с явными reference/actor (кто и по какому поводу) — для операций остатка. */
    private void recordMovement(long variantId, long warehouseId, MovementType type, int qtyDelta, int qtyAfter,
                                int reservedDelta, int reservedAfter, MovementContext ctx, String comment) {
        MovementContext c = ctx == null ? MovementContext.manual(null) : ctx;
        jdbc.update("""
                insert into inventory_movements (variant_id, warehouse_id, type, qty_delta, qty_after,
                                                 reserved_delta, reserved_after, reference_type, reference_id,
                                                 comment, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                variantId, warehouseId, type.name(), qtyDelta, qtyAfter, reservedDelta, reservedAfter,
                c.typeOrManual().name(), c.referenceId(), comment, c.actorId());
    }

    private void publishLowStockIfNeeded(long variantId, long warehouseId, InvRow after) {
        if (after == null || after.lowThreshold() == null) {
            return;
        }
        int available = after.qty() - after.reserved();
        if (available < after.lowThreshold()) {
            // AFTER_COMMIT-слушатель отреагирует только если транзакция закоммитится.
            events.publishEvent(new LowStockEvent(variantId, warehouseId, available, after.lowThreshold()));
        }
    }

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
