package com.tzevabest.iam.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Адрес пользователя (таблица {@code user_addresses}). */
@Entity
@Table(name = "user_addresses")
@Getter
@Setter
public class UserAddress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_addresses_user_id"))
    private User user;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "house", nullable = false)
    private String house;

    @Column(name = "apartment")
    private String apartment;

    @Column(name = "floor")
    private String floor;

    @Column(name = "entrance")
    private String entrance;

    @Column(name = "zip")
    private String zip;

    @Column(name = "notes")
    private String notes;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
