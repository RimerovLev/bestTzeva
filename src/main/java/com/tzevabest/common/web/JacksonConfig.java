package com.tzevabest.common.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Locale;

/**
 * Сериализация всех enum в JSON как строка в нижнем регистре с подчёркиваниями
 * ({@code IN_PROGRESS} → {@code "in_progress"}).
 * <p>
 * Обратное направление при десериализации тела покрывается флагом
 * {@code spring.jackson.mapper.accept-case-insensitive-enums=true}, а для path/query —
 * {@code StringToEnumConverterFactory}.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    Module lowerCaseEnumModule() {
        SimpleModule module = new SimpleModule("LowerCaseEnumModule");

        // Значения enum: "in_progress".
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifyEnumSerializer(SerializationConfig config, JavaType valueType,
                                                          BeanDescription beanDesc, JsonSerializer<?> serializer) {
                return new JsonSerializer<Enum<?>>() {
                    @Override
                    public void serialize(Enum<?> value, JsonGenerator gen, SerializerProvider serializers)
                            throws IOException {
                        gen.writeString(value.name().toLowerCase(Locale.ROOT));
                    }
                };
            }
        });

        // Ключи enum в Map (например Map<OrderStatus, BigDecimal> в отчётах) — тоже в нижнем регистре.
        module.addKeySerializer(Enum.class, (JsonSerializer) new JsonSerializer<Enum<?>>() {
            @Override
            public void serialize(Enum<?> value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeFieldName(value.name().toLowerCase(Locale.ROOT));
            }
        });

        return module;
    }
}
