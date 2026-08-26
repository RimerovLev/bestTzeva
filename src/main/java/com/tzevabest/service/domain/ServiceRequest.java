package com.tzevabest.service.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Заявка на услугу покраски. Таблица {@code service_requests}. */
@Entity
@Table(name = "service_requests")
@Getter
@Setter
public class ServiceRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, length = 20)
    private String requestNumber;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 16)
    private PropertyType propertyType;

    @Column(name = "area_sqm")
    private BigDecimal areaSqm;

    @Column(name = "rooms_count")
    private Integer roomsCount;

    @Column(name = "ceiling_height_m")
    private BigDecimal ceilingHeightM;

    @ElementCollection
    @CollectionTable(name = "service_request_work_types",
            joinColumns = @JoinColumn(name = "request_id",
                    foreignKey = @ForeignKey(name = "fk_service_request_work_types_request_id")))
    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 16)
    private Set<WorkType> workTypes = EnumSet.noneOf(WorkType.class);

    @Column(name = "city")
    private String city;

    @Column(name = "address")
    private String address;

    @Column(name = "preferred_date_from")
    private LocalDate preferredDateFrom;

    @Column(name = "preferred_date_to")
    private LocalDate preferredDateTo;

    @Column(name = "comment")
    private String comment;

    @Column(name = "estimated_price_min")
    private BigDecimal estimatedPriceMin;

    @Column(name = "estimated_price_max")
    private BigDecimal estimatedPriceMax;

    @Column(name = "quoted_price")
    private BigDecimal quotedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ServiceRequestStatus status = ServiceRequestStatus.NEW;

    @Column(name = "assigned_manager_id")
    private Long assignedManagerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private RequestSource source;

    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;
}
