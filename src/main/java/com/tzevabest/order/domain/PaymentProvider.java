package com.tzevabest.order.domain;

/** Платёжный провайдер. varchar + check ck_payments_provider. */
public enum PaymentProvider {
    TRANZILA, CARDCOM, GROW, MANUAL
}
