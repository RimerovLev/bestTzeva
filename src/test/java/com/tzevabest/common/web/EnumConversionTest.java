package com.tzevabest.common.web;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.common.web.convert.StringToEnumConverterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Enum ↔ строка в нижнем регистре с подчёркиваниями в обе стороны: конвертер параметров,
 * Jackson-модуль (значение и ключ Map). Формат query и тела совпадает (только регистр,
 * без дефисов).
 */
class EnumConversionTest {

    enum Status { NEW, IN_PROGRESS, DONE }

    private final StringToEnumConverterFactory factory = new StringToEnumConverterFactory();

    @Test
    void string_to_enum_accepts_lower_snake_case_any_case() {
        Converter<String, Status> converter = factory.getConverter(Status.class);
        assertThat(converter.convert("in_progress")).isEqualTo(Status.IN_PROGRESS);
        assertThat(converter.convert("IN_PROGRESS")).isEqualTo(Status.IN_PROGRESS);
        assertThat(converter.convert("done")).isEqualTo(Status.DONE);
    }

    @Test
    void string_to_enum_rejects_hyphens_like_json_does() {
        Converter<String, Status> converter = factory.getConverter(Status.class);
        assertThatThrownBy(() -> converter.convert("in-progress"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enum_value_serializes_to_lower_snake_case() throws Exception {
        ObjectMapper mapper = mapperWithModule();
        assertThat(mapper.writeValueAsString(Status.IN_PROGRESS)).isEqualTo("\"in_progress\"");
        assertThat(mapper.writeValueAsString(Status.NEW)).isEqualTo("\"new\"");
    }

    @Test
    void enum_map_key_serializes_to_lower_snake_case() throws Exception {
        ObjectMapper mapper = mapperWithModule();
        String json = mapper.writeValueAsString(Map.of(Status.IN_PROGRESS, 5));
        assertThat(json).isEqualTo("{\"in_progress\":5}");
    }

    @Test
    void enum_deserializes_case_insensitively() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        assertThat(mapper.readValue("\"in_progress\"", Status.class)).isEqualTo(Status.IN_PROGRESS);
    }

    private static ObjectMapper mapperWithModule() {
        return new ObjectMapper().registerModule(new JacksonConfig().lowerCaseEnumModule());
    }
}
