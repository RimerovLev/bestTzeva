package com.tzevabest.service.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Правило цены для калькулятора: диапазон цены за м² по (тип объекта, вид работ). */
@Entity
@Table(name = "service_pricing_rules")
@Getter
@Setter
public class ServicePricingRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 16)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 16)
    private WorkType workType;

    @Column(name = "price_per_sqm_min", nullable = false)
    private BigDecimal pricePerSqmMin;

    @Column(name = "price_per_sqm_max", nullable = false)
    private BigDecimal pricePerSqmMax;

    @Column(name = "min_order_price", nullable = false)
    private BigDecimal minOrderPrice = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
