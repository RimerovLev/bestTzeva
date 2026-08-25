package com.tzevabest.catalog.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Вариант товара (цвет + объём) — покупаемая единица. Таблица {@code product_variants}. */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_variants_product_id"))
    private Product product;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "color_code", nullable = false)
    private String colorCode;

    @Column(name = "color_hex", length = 9)
    private String colorHex;

    @Column(name = "volume_liters", nullable = false)
    private BigDecimal volumeLiters;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "old_price")
    private BigDecimal oldPrice;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
