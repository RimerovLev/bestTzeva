package com.tzevabest.common.util;

import com.tzevabest.common.web.error.ValidationException;

import java.util.Optional;

/**
 * Нормализация израильских телефонов к формату E.164 (+972…).
 * <p>
 * Принимает мобильные ({@code 05X-XXXXXXX}) и стационарные ({@code 0X-XXXXXXX}) в формах
 * {@code 05XXXXXXXX}, {@code +9725XXXXXXXX}, {@code 9725XXXXXXXX}, с пробелами/дефисами/скобками.
 * Неизвестный формат — {@link ValidationException} с кодом {@code common.phone.invalid}.
 */
public final class PhoneNormalizer {

    private static final String COUNTRY = "+972";
    private static final String LANDLINE_AREA_DIGITS = "23489";

    private PhoneNormalizer() {
    }

    /** Нормализует к E.164 (+972…) или кидает {@link ValidationException}. */
    public static String normalize(String input) {
        return COUNTRY + nsn(input)
                .orElseThrow(() -> new ValidationException("common.phone.invalid", input));
    }

    /**
     * Нормализация для ключа лимита частоты (R-52): валидный номер → E.164, чтобы 050-1234567 и
     * +972501234567 делили один бюджет. Нераспознанный формат возвращается как есть (на нём всё равно
     * упадёт валидация в сервисе) — здесь не бросаем, чтобы не подменять 429 на 400.
     */
    public static String normalizeForRateKey(String input) {
        return nsn(input).map(n -> COUNTRY + n).orElse(input == null ? "" : input.trim());
    }

    /** Валиден ли как мобильный (05X). */
    public static boolean isMobile(String input) {
        return nsn(input).filter(PhoneNormalizer::isMobileNsn).isPresent();
    }

    /** Валиден ли как стационарный (0X). */
    public static boolean isLandline(String input) {
        return nsn(input).filter(PhoneNormalizer::isLandlineNsn).isPresent();
    }

    /** Национальный значащий номер (без кода страны и ведущего 0), если формат распознан. */
    private static Optional<String> nsn(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String cleaned = input.replaceAll("[\\s\\-().]", "");
        String nsn;
        if (cleaned.startsWith("+972")) {
            nsn = cleaned.substring(4);
        } else if (cleaned.startsWith("972")) {
            nsn = cleaned.substring(3);
        } else if (cleaned.startsWith("0")) {
            nsn = cleaned.substring(1);
        } else {
            return Optional.empty();
        }
        if (!nsn.matches("\\d+")) {
            return Optional.empty();
        }
        return (isMobileNsn(nsn) || isLandlineNsn(nsn)) ? Optional.of(nsn) : Optional.empty();
    }

    private static boolean isMobileNsn(String nsn) {
        // 5X + 7 цифр = 9 цифр.
        return nsn.length() == 9 && nsn.charAt(0) == '5';
    }

    private static boolean isLandlineNsn(String nsn) {
        // X + 7 цифр = 8 цифр, X ∈ {2,3,4,8,9}.
        return nsn.length() == 8 && LANDLINE_AREA_DIGITS.indexOf(nsn.charAt(0)) >= 0;
    }
}
