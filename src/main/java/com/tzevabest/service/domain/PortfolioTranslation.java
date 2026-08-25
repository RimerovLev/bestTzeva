package com.tzevabest.service.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Перевод проекта портфолио. Уникальность (project_id, locale). */
@Entity
@Table(name = "portfolio_translations")
@Getter
@Setter
public class PortfolioTranslation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_portfolio_translations_project_id"))
    private PortfolioProject project;

    @Column(name = "locale", nullable = false, length = 5)
    private String locale;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;
}
