package com.tzevabest.catalog;

import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.service.SkuGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** T-4.2.2: формат SKU и кодирование объёмов (чистые тесты). */
class SkuGeneratorTest {

    @Test
    void generates_brand_type_color_volume() {
        assertThat(SkuGenerator.generate("TMB", PaintType.INTERIOR, "RAL9010", new BigDecimal("0.5")))
                .isEqualTo("TMB-INT-RAL9010-05L");
        assertThat(SkuGenerator.generate("tmb", PaintType.EXTERIOR, "ral-1000", new BigDecimal("2.5")))
                .isEqualTo("TMB-EXT-RAL1000-25L");
    }

    @Test
    void encodes_non_standard_volumes() {
        assertThat(SkuGenerator.volumeCode(new BigDecimal("0.75"))).isEqualTo("075L");
        assertThat(SkuGenerator.volumeCode(new BigDecimal("2.5"))).isEqualTo("25L");
        assertThat(SkuGenerator.volumeCode(new BigDecimal("20"))).isEqualTo("20L");
        assertThat(SkuGenerator.volumeCode(new BigDecimal("1.0"))).isEqualTo("01L");
    }

    @Test
    void validates_format() {
        assertThat(SkuGenerator.isValid("TMB-INT-RAL9010-05L")).isTrue();
        assertThat(SkuGenerator.isValid("not-a-sku")).isFalse();
        assertThat(SkuGenerator.isValid("TMB-INTERIOR-RAL9010-05")).isFalse(); // нет L, тип длинный
    }
}
