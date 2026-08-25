package com.tzevabest.inventory.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Остаток по варианту на складе. Покупаемая единица — вариант каталога, поэтому
 * {@code variantId} хранится как идентификатор (кросс-модульная ссылка без JPA-связи
 * на domain модуля catalog). Optimistic lock через {@link Version}.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
public class Inventory extends BaseEntity {

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_warehouse_id"))
    private Warehouse warehouse;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
