package com.tzevabest.request.dto;

import jakarta.validation.constraints.NotBlank;

/** Внутренний комментарий по заявке (виден только персоналу). */
public record ProductRequestCommentInput(@NotBlank String text) {
}
