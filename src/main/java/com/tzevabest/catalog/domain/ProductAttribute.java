package com.tzevabest.catalog.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/** Определение дополнительной характеристики (EAV). Таблица {@code product_attributes}. */
@Entity
@Table(name = "product_attributes")
@Getter
@Setter
public class ProductAttribute extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_translations")
    private Map<String, String> nameTranslations;
}
