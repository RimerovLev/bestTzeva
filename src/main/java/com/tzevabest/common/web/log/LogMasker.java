package com.tzevabest.common.web.log;

import java.util.regex.Pattern;

/**
 * Маскирование чувствительных данных в логах (T-14.1): пароли/токены/секреты, номера карт, телефоны,
 * email. Реализовано как утилита (а не «дисциплина разработчика»), используется logback-конвертером.
 */
public final class LogMasker {

    private static final Pattern SECRET = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|secret|otp|api[_-]?key)\\b\\s*[=:]\\s*\"?[^\\s\",}]+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._-]+");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-])[\\w.+-]*@([\\w-]+\\.[\\w.-]+)");
    private static final Pattern PHONE = Pattern.compile("(?:\\+972|972|0)\\d[\\d -]{6,}\\d");

    private LogMasker() {
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String m = message;
        m = SECRET.matcher(m).replaceAll(matcher -> matcher.group(1) + "=***");
        m = BEARER.matcher(m).replaceAll("Bearer ***");
        m = CARD.matcher(m).replaceAll("****");
        m = EMAIL.matcher(m).replaceAll("$1***@$2");
        m = PHONE.matcher(m).replaceAll(matcher -> {
            String digits = matcher.group().replaceAll("\\D", "");
            String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
            return "***" + last4;
        });
        return m;
    }
}
