package com.tzevabest.catalog.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Товар. Покупаемая единица — {@link ProductVariant}. Таблица {@code products}. */
@Entity
@Table(name = "products")
@Getter
@Setter
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_products_category_id"))
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_products_brand_id"))
    private Brand brand;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "paint_type", nullable = false, length = 16)
    private PaintType paintType;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_area", nullable = false, length = 16)
    private UsageArea usageArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "finish", nullable = false, length = 16)
    private Finish finish;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_type", nullable = false, length = 16)
    private BaseType baseType;

    @Column(name = "coverage_sqm_per_liter")
    private BigDecimal coverageSqmPerLiter;

    @Column(name = "drying_time_hours")
    private BigDecimal dryingTimeHours;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    @Column(name = "rating_avg", nullable = false)
    private BigDecimal ratingAvg = new BigDecimal("0.0");

    @Column(name = "rating_count", nullable = false)
    private int ratingCount = 0;

    @OneToMany(mappedBy = "product")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    private List<ProductTranslation> translations = new ArrayList<>();
}
