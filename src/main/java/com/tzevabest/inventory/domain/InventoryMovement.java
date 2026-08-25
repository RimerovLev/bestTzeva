package com.tzevabest.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * Запись журнала складских движений. Только вставка — {@link Immutable} запрещает UPDATE
 * на уровне ORM. Кросс-модульные ссылки ({@code variantId}, {@code createdBy}) — как id.
 */
@Entity
@Table(name = "inventory_movements")
@Immutable
@Getter
@Setter
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private MovementType type;

    @Column(name = "qty_delta", nullable = false)
    private int qtyDelta;

    @Column(name = "qty_after", nullable = false)
    private int qtyAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 16)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;
}
