package com.tzevabest.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.order.domain.OrderStatus;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что настройки Jackson (lowercase-enum модуль + snake_case) реально применены
 * к <b>боевому</b> ObjectMapper приложения, а не только к собранному вручную в unit-тесте.
 */
class JacksonWiringTest extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void app_object_mapper_serializes_enum_lowercase() throws Exception {
        assertThat(objectMapper.writeValueAsString(OrderStatus.AWAITING_PAYMENT))
                .isEqualTo("\"awaiting_payment\"");
    }

    @Test
    void app_object_mapper_uses_snake_case() throws Exception {
        String json = objectMapper.writeValueAsString(PageResponse.of(List.<String>of(), 0, 20, 45));
        assertThat(json).contains("\"total_elements\":45").contains("\"total_pages\":3");
    }
}
