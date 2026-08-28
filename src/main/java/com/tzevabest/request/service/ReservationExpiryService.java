package com.tzevabest.request.service;

import com.tzevabest.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Уборка просроченных резервов: снимает резерв и переводит заявку в LOST (reservation_expired).
 * <p>
 * Безопасна на нескольких инстансах (R-42): вся уборка под распределённой блокировкой в Redis
 * ({@code SET NX} с TTL) — параллельно работает только один инстанс. Обрабатывается пачками по
 * {@value #BATCH}, каждая в своей транзакции, чтобы не держать блокировки строк inventory на всём
 * проходе. Само снятие резерва идемпотентно (условный апдейт в {@link InventoryService}).
 */
@Service
@RequiredArgsConstructor
public class ReservationExpiryService {

    static final int BATCH = 100;
    private static final String LOCK_KEY = "inventory:reservation-sweep:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final InventoryService inventory;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final PlatformTransactionManager txManager;

    /** Прогнать уборку. Возвращает число заявок, переведённых в LOST. */
    public int sweep() {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return 0; // уборку уже ведёт другой инстанс
        }
        try {
            TransactionTemplate tx = new TransactionTemplate(txManager);
            int total = 0;
            int processed;
            do {
                processed = tx.execute(status -> processBatch());
                total += processed;
            } while (processed == BATCH);
            return total;
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    private int processBatch() {
        List<Long> expired = inventory.findRequestsWithExpiredReservations(BATCH);
        for (Long requestId : expired) {
            inventory.releaseExpired(requestId);
            String from = jdbc.queryForObject("select status from product_requests where id = ?", String.class, requestId);
            if (!"LOST".equals(from)) {
                jdbc.update("update product_requests set status = 'LOST', updated_at = now() where id = ?", requestId);
                jdbc.update("""
                        insert into product_request_history (request_id, from_status, to_status, comment)
                        values (?, ?, 'LOST', 'reservation_expired')""", requestId, from);
            }
        }
        return expired.size();
    }
}
