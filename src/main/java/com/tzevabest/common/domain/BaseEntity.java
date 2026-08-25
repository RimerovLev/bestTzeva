package com.tzevabest.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import org.hibernate.Hibernate;

import java.time.Instant;
import java.util.Objects;

/**
 * Базовый класс сущностей: {@code id} и аудит времени.
 * <p>
 * {@code createdAt}/{@code updatedAt} проставляются приложением в lifecycle-колбэках
 * (@PrePersist/@PreUpdate) — без {@code @Generated}, чтобы Hibernate не делал лишний SELECT
 * для вычитывания значений на каждый UPDATE (важно для пакетных операций). Триггер
 * {@code set_updated_at} и {@code default now()} в БД остаются страховкой для правок в обход ORM.
 * <p>
 * {@code equals}/{@code hashCode} — на основе {@code id}: две несохранённые сущности равны
 * только по ссылке, {@code hashCode} стабилен (по классу), чтобы не менялся при присвоении id.
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        BaseEntity that = (BaseEntity) other;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
