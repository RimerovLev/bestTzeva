package com.tzevabest.catalog.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Перевод категории. Таблица {@code category_translations}, уникальность (category_id, locale). */
@Entity
@Table(name = "category_translations")
@Getter
@Setter
public class CategoryTranslation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_category_translations_category_id"))
    private Category category;

    @Column(name = "locale", nullable = false, length = 5)
    private String locale;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "meta_title")
    private String metaTitle;

    @Column(name = "meta_description")
    private String metaDescription;
}
