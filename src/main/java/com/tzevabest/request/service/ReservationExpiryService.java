package com.tzevabest.request.service;

import com.tzevabest.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Уборка просроченных резервов: по истечении TTL резерв снимается, а заявка уходит в LOST с причиной
 * reservation_expired (гонка за последними банками никуда не делась — резерв держит остаток 3 суток).
 */
@Service
@RequiredArgsConstructor
public class ReservationExpiryService {

    private final InventoryService inventory;
    private final JdbcTemplate jdbc;

    @Transactional
    public int sweep() {
        List<Long> expired = inventory.findRequestsWithExpiredReservations();
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
