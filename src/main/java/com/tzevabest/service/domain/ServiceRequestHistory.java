package com.tzevabest.service.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.generator.EventType;

import java.time.Instant;

/** Запись журнала смены статуса заявки (append-only, {@link Immutable}). */
@Entity
@Table(name = "service_request_history")
@Immutable
@Getter
@Setter
public class ServiceRequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_service_request_history_request_id"))
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    private ServiceRequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    private ServiceRequestStatus toStatus;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;
}
