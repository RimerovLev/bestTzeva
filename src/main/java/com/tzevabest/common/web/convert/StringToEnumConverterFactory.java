package com.tzevabest.common.web.convert;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import java.util.Locale;

/**
 * Конвертирует строку в enum для path/query-параметров: принимает нижний регистр с
 * подчёркиваниями ({@code in_progress}) в любом регистре. Формат намеренно совпадает с
 * разбором enum в теле запроса (только регистр, без дефисов), чтобы {@code ?status=...} и
 * {@code {"status":...}} вели себя одинаково. Обратное направление (enum → строка в нижнем
 * регистре) обеспечивает Jackson-модуль в {@code JacksonConfig}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class StringToEnumConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return new StringToEnum<>(targetType);
    }

    private static final class StringToEnum<T extends Enum> implements Converter<String, T> {

        private final Class<T> enumType;

        StringToEnum(Class<T> enumType) {
            this.enumType = enumType;
        }

        @Override
        public T convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            String normalized = source.trim().toUpperCase(Locale.ROOT);
            return (T) Enum.valueOf(enumType, normalized);
        }
    }
}
