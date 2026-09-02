package com.tzevabest.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** Запрос по субъекту ПДн (телефон или email). */
public record SubjectRequest(@NotBlank String contact) {
}
