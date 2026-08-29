package com.tzevabest.service.service;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;
import com.tzevabest.service.dto.CalculateRequest;
import com.tzevabest.service.dto.CalculateResponse;
import com.tzevabest.service.dto.WorkTypeCostView;
import com.tzevabest.service.pricing.PricingCalculator;
import com.tzevabest.service.pricing.PricingInput;
import com.tzevabest.service.pricing.PricingOption;
import com.tzevabest.service.pricing.PricingResult;
import com.tzevabest.service.pricing.PricingRule;
import com.tzevabest.service.pricing.Ruleset;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Предварительный расчёт стоимости услуг (T-11.2): собирает тарифы и коэффициенты из БД в
 * {@link Ruleset} и делегирует чистому {@link PricingCalculator}. Результат — всегда оценка, а не
 * окончательная цена (флаг is_estimate + оговорка на локали запроса).
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final JdbcTemplate jdbc;
    private final MessageSource messages;

    /** Публичный расчёт с разбивкой и оговоркой. */
    public CalculateResponse calculate(CalculateRequest req, String localeParam) {
        PricingResult result = compute(req.propertyType(), req.areaSqm(), req.ceilingHeightM(),
                req.workTypes(), req.options(), req.urgent());
        List<WorkTypeCostView> breakdown = result.breakdown().stream()
                .map(c -> new WorkTypeCostView(c.workType(), c.areaSqm(), c.priceMin(), c.priceMax()))
                .toList();
        String disclaimer = messages.getMessage("service.pricing.disclaimer", null,
                Locale.forLanguageTag(resolveLocale(localeParam)));
        return new CalculateResponse(result.priceMin(), result.priceMax(), breakdown, true, disclaimer);
    }

    /** Оценка для снимка в заявку (без опций/срочности — на этапе приёма их ещё нет). */
    public PricingResult estimateAtIntake(PropertyType propertyType, BigDecimal areaSqm,
                                          BigDecimal ceilingHeightM, Set<WorkType> workTypes) {
        if (areaSqm == null || workTypes == null || workTypes.isEmpty()) {
            return null;
        }
        return compute(propertyType, areaSqm, ceilingHeightM, workTypes, Set.of(), false);
    }

    private PricingResult compute(PropertyType propertyType, BigDecimal areaSqm, BigDecimal ceilingHeightM,
                                  Set<WorkType> workTypes, Set<PricingOption> options, boolean urgent) {
        Ruleset ruleset = loadRuleset(propertyType);
        PricingInput input = new PricingInput(areaSqm, ceilingHeightM, workTypes,
                options == null ? Set.of() : options, urgent);
        return PricingCalculator.calculate(input, ruleset);
    }

    private Ruleset loadRuleset(PropertyType propertyType) {
        Map<WorkType, PricingRule> rules = new EnumMap<>(WorkType.class);
        jdbc.query("select work_type, price_per_sqm_min, price_per_sqm_max, min_order_price "
                        + "from service_pricing_rules where property_type = ? and is_active",
                (RowCallbackHandler) rs -> rules.put(WorkType.valueOf(rs.getString("work_type")),
                        new PricingRule(rs.getBigDecimal("price_per_sqm_min"), rs.getBigDecimal("price_per_sqm_max"),
                                rs.getBigDecimal("min_order_price"))),
                propertyType.name());

        Map<String, BigDecimal> coefficients = new HashMap<>();
        jdbc.query("select code, multiplier from service_pricing_coefficients where is_active",
                (RowCallbackHandler) rs -> coefficients.put(rs.getString("code"), rs.getBigDecimal("multiplier")));

        return new Ruleset(rules, coefficients);
    }

    private static String resolveLocale(String localeParam) {
        if (localeParam != null) {
            String lower = localeParam.toLowerCase(Locale.ROOT);
            if (Set.of("he", "ru", "en").contains(lower)) {
                return lower;
            }
        }
        return "he";
    }
}
