package com.tzevabest.request.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Позиция заявки со снимками полей товара на момент подачи (кросс-модульная ссылка на вариант — по id).
 * Таблица {@code product_request_items}. created_at проставляет БД (не мапится).
 */
@Entity
@Table(name = "product_request_items")
@Getter
@Setter
public class ProductRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_request_items_request"))
    private ProductRequest request;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "sku_snapshot", nullable = false, length = 64)
    private String skuSnapshot;

    @Column(name = "color_snapshot", length = 64)
    private String colorSnapshot;

    @Column(name = "volume_liters_snapshot")
    private BigDecimal volumeLitersSnapshot;

    @Column(name = "price_snapshot", nullable = false)
    private BigDecimal priceSnapshot;
}
