package com.tzevabest.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Запрос presigned-ссылок на загрузку фото помещения (до 10). */
public record PresignRequest(
        @NotEmpty @Size(max = 10) @Valid List<PresignItem> files) {
}
