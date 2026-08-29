package com.tzevabest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Один файл для presigned-загрузки. */
public record PresignItem(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 100) String contentType) {
}
