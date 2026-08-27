package com.tzevabest.request.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Заявка на товар (лид). Таблица {@code product_requests}. */
@Entity
@Table(name = "product_requests")
@Getter
@Setter
public class ProductRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, length = 20)
    private String requestNumber;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "comment")
    private String comment;

    @Column(name = "preferred_contact_time", length = 120)
    private String preferredContactTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ProductRequestStatus status = ProductRequestStatus.NEW;

    @Column(name = "assigned_manager_id")
    private Long assignedManagerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private ProductRequestSource source;

    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    @Column(name = "policy_version", nullable = false, length = 32)
    private String policyVersion;
}
