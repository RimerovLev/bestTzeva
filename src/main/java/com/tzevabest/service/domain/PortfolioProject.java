package com.tzevabest.service.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Проект портфолио. Таблица {@code portfolio_projects}. */
@Entity
@Table(name = "portfolio_projects")
@Getter
@Setter
public class PortfolioProject extends BaseEntity {

    @Column(name = "slug", nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", length = 16)
    private PropertyType propertyType;

    @Column(name = "city")
    private String city;

    @Column(name = "area_sqm")
    private BigDecimal areaSqm;

    @ElementCollection
    @CollectionTable(name = "portfolio_project_work_types",
            joinColumns = @JoinColumn(name = "project_id",
                    foreignKey = @ForeignKey(name = "fk_portfolio_project_work_types_project_id")))
    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 16)
    private Set<WorkType> workTypes = EnumSet.noneOf(WorkType.class);

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "completed_at")
    private LocalDate completedAt;

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
