package com.tzevabest.inventory.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Реакция на {@link LowStockEvent}. Слушает ПОСЛЕ коммита (AFTER_COMMIT): сигнал имеет смысл только
 * когда остаток действительно уменьшился, а не в откатившейся транзакции. Пока — запись в лог для
 * оповещения закупок; отдельный канал уведомлений можно подключить здесь же.
 */
@Component
@Slf4j
public class LowStockListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        log.warn("Low stock: variant={} warehouse={} available={} below threshold={}",
                event.variantId(), event.warehouseId(), event.available(), event.threshold());
    }
}
