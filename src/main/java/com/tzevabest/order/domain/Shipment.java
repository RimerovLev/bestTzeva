package com.tzevabest.order.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Отгрузка заказа. Таблица {@code shipments}. */
@Entity
@Table(name = "shipments")
@Getter
@Setter
public class Shipment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_shipments_order_id"))
    private Order order;

    @Column(name = "carrier", length = 120)
    private String carrier;

    @Column(name = "tracking_number", length = 128)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "notes", length = 1000)
    private String notes;
}
