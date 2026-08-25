package com.tzevabest.order.domain;

/** Область применения купона. varchar + check ck_coupons_applies_to. */
public enum CouponAppliesTo {
    ALL, CATEGORY, PRODUCT, BRAND
}
