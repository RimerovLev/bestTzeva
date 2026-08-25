package com.tzevabest.common.util;

import com.tzevabest.common.web.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            // мобильные, разные формы ввода → единый E.164
            "0501234567,        +972501234567",
            "050-123-4567,      +972501234567",
            "050 123 4567,      +972501234567",
            "'(050) 123-4567',  +972501234567",
            "+972501234567,     +972501234567",
            "972501234567,      +972501234567",
            "+972 50 123 4567,  +972501234567",
            "0521234567,        +972521234567",
            "054-9876543,       +972549876543",
            // стационарные
            "031234567,         +97231234567",
            "03-123-4567,       +97231234567",
            "02-1234567,        +97221234567",
            "+97291234567,      +97291234567",
            "08 123 4567,       +97281234567",
    })
    void normalizes_valid_numbers_to_e164(String input, String expected) {
        assertThat(PhoneNormalizer.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",              // слишком короткий
            "0512345",            // мобильный неполный
            "0601234567",         // не мобильный (не 5) и не стационарный
            "+11234567890",       // чужой код страны
            "abcdefg",            // не цифры
            "05012345678",        // мобильный слишком длинный
            "+972",               // пусто после кода
    })
    void rejects_invalid_numbers(String input) {
        assertThatThrownBy(() -> PhoneNormalizer.normalize(input))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> PhoneNormalizer.normalize(null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void classifies_mobile_and_landline() {
        assertThat(PhoneNormalizer.isMobile("0501234567")).isTrue();
        assertThat(PhoneNormalizer.isLandline("0501234567")).isFalse();
        assertThat(PhoneNormalizer.isLandline("03-1234567")).isTrue();
        assertThat(PhoneNormalizer.isMobile("03-1234567")).isFalse();
        assertThat(PhoneNormalizer.isMobile("nonsense")).isFalse();
    }
}
