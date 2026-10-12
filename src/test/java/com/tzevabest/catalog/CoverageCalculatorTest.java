package com.tzevabest.catalog;

import com.tzevabest.catalog.dto.CoverageCalcResult;
import com.tzevabest.catalog.dto.VariantCoverage;
import com.tzevabest.catalog.service.CoverageCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Граничные случаи расчёта расхода краски (B-04). Чистая логика, без Spring. */
class CoverageCalculatorTest {

    private static CoverageCalculator.Variant variant(long id, String volume) {
        return new CoverageCalculator.Variant(id, "SKU-" + id, "RAL1", "#FFFFFF",
                new BigDecimal(volume), new BigDecimal("100.00"));
    }

    @Test
    void computes_cans_and_leftover_per_variant() {
        // расход 10 м²/л, 20 м² в 2 слоя → нужно 40/10 = 4 л
        CoverageCalcResult result = CoverageCalculator.calculate(
                new BigDecimal("10"), new BigDecimal("20"), 2,
                List.of(variant(1, "2.5"), variant(2, "5")));

        assertThat(result.calculable()).isTrue();
        assertThat(result.variants()).hasSize(2);

        VariantCoverage small = result.variants().get(0); // 2.5 л
        assertThat(small.cans()).isEqualTo(2);                       // ceil(4 / 2.5) = 2
        assertThat(small.leftoverLiters()).isEqualByComparingTo("1.00"); // 2*2.5 - 4

        VariantCoverage big = result.variants().get(1);   // 5 л
        assertThat(big.cans()).isEqualTo(1);                         // ceil(4 / 5) = 1
        assertThat(big.leftoverLiters()).isEqualByComparingTo("1.00"); // 5 - 4
    }

    @Test
    void area_smaller_than_one_can_still_needs_one_can() {
        // нужно всего 0.3 л — банка меньше не бывает, значит одна банка
        CoverageCalcResult result = CoverageCalculator.calculate(
                new BigDecimal("10"), new BigDecimal("3"), 1, List.of(variant(1, "2.5")));

        assertThat(result.calculable()).isTrue();
        VariantCoverage v = result.variants().get(0);
        assertThat(v.cans()).isEqualTo(1);
        assertThat(v.leftoverLiters()).isEqualByComparingTo("2.20"); // 2.5 - 0.3
    }

    @Test
    void missing_coverage_is_not_calculable() {
        CoverageCalcResult result = CoverageCalculator.calculate(
                null, new BigDecimal("20"), 2, List.of(variant(1, "2.5")));

        assertThat(result.calculable()).isFalse();
        assertThat(result.variants()).isEmpty();
        assertThat(result.coverageSqmPerLiter()).isNull();
    }

    @Test
    void zero_coverage_is_not_calculable() {
        CoverageCalcResult result = CoverageCalculator.calculate(
                BigDecimal.ZERO, new BigDecimal("20"), 2, List.of(variant(1, "2.5")));
        assertThat(result.calculable()).isFalse();
    }

    @Test
    void variant_without_volume_is_skipped() {
        CoverageCalculator.Variant noVolume =
                new CoverageCalculator.Variant(9, "SKU-9", "RAL1", "#000", null, new BigDecimal("50"));
        CoverageCalcResult result = CoverageCalculator.calculate(
                new BigDecimal("10"), new BigDecimal("20"), 2, List.of(noVolume, variant(1, "2.5")));

        assertThat(result.variants()).extracting(VariantCoverage::variantId).containsExactly(1L);
    }
}
