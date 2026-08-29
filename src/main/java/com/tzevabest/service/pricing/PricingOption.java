package com.tzevabest.service.pricing;

/** Доп-опции работ. Код совпадает с service_pricing_coefficients.code (OPT_*). */
public enum PricingOption {
    FURNITURE_REMOVAL, SURFACE_PREP, PRIMING, HEIGHT_WORK;

    public String coefficientCode() {
        return "OPT_" + name();
    }
}
