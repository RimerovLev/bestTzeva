package com.tzevabest.order.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Позиция заказа со снимками каталога (история заказа не зависит от изменений товара). */
@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_items_order_id"))
    private Order order;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "variant_name_snapshot")
    private String variantNameSnapshot;

    @Column(name = "sku_snapshot", nullable = false)
    private String skuSnapshot;

    @Column(name = "color_code_snapshot")
    private String colorCodeSnapshot;

    @Column(name = "volume_liters_snapshot")
    private BigDecimal volumeLitersSnapshot;

    @Column(name = "weight_kg_snapshot")
    private BigDecimal weightKgSnapshot;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;
}
