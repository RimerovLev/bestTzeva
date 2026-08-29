package com.tzevabest.service;

import com.tzevabest.service.domain.WorkType;
import com.tzevabest.service.pricing.PricingCalculator;
import com.tzevabest.service.pricing.PricingInput;
import com.tzevabest.service.pricing.PricingOption;
import com.tzevabest.service.pricing.PricingResult;
import com.tzevabest.service.pricing.PricingRule;
import com.tzevabest.service.pricing.Ruleset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** T-11.2: чистая арифметика калькулятора — быстрые юнит-тесты без Spring/БД. */
class PricingCalculatorTest {

    private static final Map<String, BigDecimal> COEFFS = Map.of(
            "CEILING_HEIGHT_OVER_3M", new BigDecimal("1.2"),
            "URGENCY", new BigDecimal("1.3"),
            "OPT_SURFACE_PREP", new BigDecimal("1.15"));

    private Ruleset walls(String min, String max, String minOrder) {
        return new Ruleset(
                Map.of(WorkType.WALLS, new PricingRule(new BigDecimal(min), new BigDecimal(max), new BigDecimal(minOrder))),
                COEFFS);
    }

    private PricingInput input(String area, String height, boolean urgent, Set<PricingOption> opts) {
        return new PricingInput(new BigDecimal(area), height == null ? null : new BigDecimal(height),
                EnumSet.of(WorkType.WALLS), opts, urgent);
    }

    @Test
    void apartment_walls_base_case() {
        // площадь 100, высота 2.7 → стены = 4·√100·2.7 = 108 м²; 108·25=2700 .. 108·40=4320
        PricingResult r = PricingCalculator.calculate(input("100", "2.70", false, Set.of()), walls("25", "40", "500"));
        assertThat(r.priceMin()).isEqualByComparingTo("2700");
        assertThat(r.priceMax()).isEqualByComparingTo("4320");
        assertThat(r.estimate()).isTrue();
        assertThat(r.breakdown()).hasSize(1);
        assertThat(r.breakdown().get(0).areaSqm()).isEqualByComparingTo("108.00");
    }

    @Test
    void ceiling_height_coefficient_raises_price() {
        PricingResult base = PricingCalculator.calculate(input("100", "2.70", false, Set.of()), walls("25", "40", "500"));
        // высота 3.5 → площадь стен больше И множитель 1.2
        PricingResult tall = PricingCalculator.calculate(input("100", "3.50", false, Set.of()), walls("25", "40", "500"));
        assertThat(tall.priceMin()).isGreaterThan(base.priceMin());
        // 4·10·3.5=140; 140·25=3500·1.2=4200 ; 140·40=5600·1.2=6720
        assertThat(tall.priceMin()).isEqualByComparingTo("4200");
        assertThat(tall.priceMax()).isEqualByComparingTo("6720");
    }

    @Test
    void minimum_order_price_kicks_in_for_tiny_area() {
        // площадь 1 → стены 10.8 м²; 10.8·25=270 .. 10.8·40=432 — ниже минимума 500
        PricingResult r = PricingCalculator.calculate(input("1", "2.70", false, Set.of()), walls("25", "40", "500"));
        assertThat(r.priceMin()).isEqualByComparingTo("500");
        assertThat(r.priceMax()).isEqualByComparingTo("500");
    }

    @Test
    void rounding_min_down_max_up_to_ten() {
        // 108·25.05=2705.4 → вниз до 2700 ; 108·40.01=4321.08 → вверх до 4330
        PricingResult r = PricingCalculator.calculate(input("100", "2.70", false, Set.of()), walls("25.05", "40.01", "500"));
        assertThat(r.priceMin()).isEqualByComparingTo("2700");
        assertThat(r.priceMax()).isEqualByComparingTo("4330");
    }

    @Test
    void options_and_urgency_multiply() {
        PricingResult r = PricingCalculator.calculate(
                input("100", "2.70", true, Set.of(PricingOption.SURFACE_PREP)), walls("25", "40", "500"));
        // 2700·1.3·1.15 = 4036.5 → вниз 4030 ; 4320·1.3·1.15=6458.4 → вверх 6460
        assertThat(r.priceMin()).isEqualByComparingTo("4030");
        assertThat(r.priceMax()).isEqualByComparingTo("6460");
    }
}
