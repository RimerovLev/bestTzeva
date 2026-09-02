package com.tzevabest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Внутренний комментарий персонала. */
public record CommentInput(@NotBlank @Size(max = 2000) String text) {
}
