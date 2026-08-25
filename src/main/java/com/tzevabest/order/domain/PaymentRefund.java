package com.tzevabest.order.domain;

import com.tzevabest.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Возврат по платежу. Таблица {@code payment_refunds}. */
@Entity
@Table(name = "payment_refunds")
@Getter
@Setter
public class PaymentRefund extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_refunds_payment_id"))
    private Payment payment;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "provider_refund_id")
    private String providerRefundId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "created_by")
    private Long createdBy;
}
