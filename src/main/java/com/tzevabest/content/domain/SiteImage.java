package com.tzevabest.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Картинка сайта для слота. Таблица {@code site_images}; ключ — сам слот. */
@Entity
@Table(name = "site_images")
@Getter
@Setter
public class SiteImage {

    @Id
    @Column(name = "slot", length = 40, nullable = false, updatable = false)
    private String slot;

    @Column(name = "object_key", length = 400, nullable = false)
    private String objectKey;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
