package com.tzevabest.catalog.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Перевод товара. Таблица {@code product_translations}, уникальность (product_id, locale). */
@Entity
@Table(name = "product_translations")
@Getter
@Setter
public class ProductTranslation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_translations_product_id"))
    private Product product;

    @Column(name = "locale", nullable = false, length = 5)
    private String locale;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "description")
    private String description;

    @Column(name = "usage_instructions")
    private String usageInstructions;

    @Column(name = "meta_title")
    private String metaTitle;

    @Column(name = "meta_description")
    private String metaDescription;
}
