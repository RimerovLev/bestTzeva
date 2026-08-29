package com.tzevabest.service.pricing;

import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Расчёт предварительной стоимости покраски — чистая арифметика без Spring и БД (T-11.2): на вход
 * параметры и тарифы, на выход разложенные суммы. Единственное место, где считаются деньги.
 * <p>
 * Площадь стен оценивается из площади пола и высоты потолков: периметр ≈ 4·√(площадь пола),
 * площадь стен = периметр × высота. Погрешность формы помещения закладывается в разброс min–max
 * (см. docs/service-pricing.md).
 */
public final class PricingCalculator {

    private static final BigDecimal DEFAULT_HEIGHT = new BigDecimal("2.70");
    private static final BigDecimal HEIGHT_THRESHOLD = new BigDecimal("3.00");
    private static final BigDecimal ACCENT_FRACTION = new BigDecimal("0.15"); // дерево/металл/декор — доля пола
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal PERIMETER_FACTOR = new BigDecimal("4");
    private static final MathContext MC = new MathContext(12);

    private PricingCalculator() {
    }

    public static PricingResult calculate(PricingInput input, Ruleset ruleset) {
        BigDecimal area = input.areaSqm();
        BigDecimal height = input.ceilingHeightM() == null ? DEFAULT_HEIGHT : input.ceilingHeightM();
        BigDecimal multiplier = multiplier(input, height, ruleset);

        List<WorkTypeCost> breakdown = new ArrayList<>();
        BigDecimal sumMin = BigDecimal.ZERO;
        BigDecimal sumMax = BigDecimal.ZERO;
        BigDecimal minOrder = BigDecimal.ZERO;

        for (WorkType wt : input.workTypes()) {
            PricingRule rule = ruleset.rules().get(wt);
            if (rule == null) {
                continue; // тариф на этот вид работ не задан — пропускаем
            }
            BigDecimal basis = areaBasis(wt, area, height);
            BigDecimal lineMin = basis.multiply(rule.pricePerSqmMin()).multiply(multiplier);
            BigDecimal lineMax = basis.multiply(rule.pricePerSqmMax()).multiply(multiplier);
            breakdown.add(new WorkTypeCost(wt, scale2(basis), scale2(lineMin), scale2(lineMax)));
            sumMin = sumMin.add(lineMin);
            sumMax = sumMax.add(lineMax);
            minOrder = minOrder.max(rule.minOrderPrice());
        }

        // Минимальная стоимость заказа: если расчёт ниже — поднимаем до неё.
        if (sumMin.compareTo(minOrder) < 0) {
            sumMin = minOrder;
        }
        if (sumMax.compareTo(minOrder) < 0) {
            sumMax = minOrder;
        }

        return new PricingResult(roundDownTo10(sumMin), roundUpTo10(sumMax), breakdown, true);
    }

    private static BigDecimal multiplier(PricingInput input, BigDecimal height, Ruleset ruleset) {
        BigDecimal m = BigDecimal.ONE;
        if (height.compareTo(HEIGHT_THRESHOLD) > 0) {
            m = m.multiply(coeff(ruleset, "CEILING_HEIGHT_OVER_3M"));
        }
        if (input.urgent()) {
            m = m.multiply(coeff(ruleset, "URGENCY"));
        }
        if (input.options() != null) {
            for (PricingOption option : input.options()) {
                m = m.multiply(coeff(ruleset, option.coefficientCode()));
            }
        }
        return m;
    }

    private static BigDecimal coeff(Ruleset ruleset, String code) {
        BigDecimal value = ruleset.coefficients().get(code);
        return value == null ? BigDecimal.ONE : value; // отсутствует — нейтрально
    }

    /** Расчётная площадь по виду работ. */
    private static BigDecimal areaBasis(WorkType wt, BigDecimal floorArea, BigDecimal height) {
        return switch (wt) {
            case WALLS, FACADE -> PERIMETER_FACTOR.multiply(floorArea.sqrt(MC)).multiply(height);
            case CEILING -> floorArea;
            case WOOD, METAL, DECORATIVE -> floorArea.multiply(ACCENT_FRACTION);
        };
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal roundDownTo10(BigDecimal v) {
        return v.divide(TEN, 0, RoundingMode.FLOOR).multiply(TEN);
    }

    private static BigDecimal roundUpTo10(BigDecimal v) {
        return v.divide(TEN, 0, RoundingMode.CEILING).multiply(TEN);
    }
}
