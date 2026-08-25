package com.tzevabest.catalog.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Значение характеристики товара (EAV). Таблица {@code product_attribute_values}. */
@Entity
@Table(name = "product_attribute_values")
@Getter
@Setter
public class ProductAttributeValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_attribute_values_product_id"))
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_attribute_values_attribute_id"))
    private ProductAttribute attribute;

    @Column(name = "value", nullable = false)
    private String value;
}
