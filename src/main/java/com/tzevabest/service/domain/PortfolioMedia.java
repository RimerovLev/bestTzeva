package com.tzevabest.service.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Медиа портфолио (before/after/process); pair_group связывает до↔после. */
@Entity
@Table(name = "portfolio_media")
@Getter
@Setter
public class PortfolioMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_portfolio_media_project_id"))
    private PortfolioProject project;

    @Column(name = "url", nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private PortfolioMediaKind kind;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "pair_group", length = 64)
    private String pairGroup;
}
