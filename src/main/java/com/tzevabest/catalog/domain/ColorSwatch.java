package com.tzevabest.catalog.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/** Справочник цветов. Таблица {@code color_swatches}. */
@Entity
@Table(name = "color_swatches")
@Getter
@Setter
public class ColorSwatch extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "system", nullable = false, length = 16)
    private ColorSystem system;

    @Column(name = "hex", length = 9)
    private String hex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_translations")
    private Map<String, String> nameTranslations;

    @Column(name = "collection")
    private String collection;

    @Column(name = "is_popular", nullable = false)
    private boolean popular = false;
}
