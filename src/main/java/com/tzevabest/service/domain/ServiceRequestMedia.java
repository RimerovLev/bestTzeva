package com.tzevabest.service.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/** Фото/медиа помещения от клиента. Таблица {@code service_request_media}. */
@Entity
@Table(name = "service_request_media")
@Getter
@Setter
public class ServiceRequestMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_service_request_media_request_id"))
    private ServiceRequest request;

    @Column(name = "url", nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ServiceMediaType type;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant uploadedAt;
}
