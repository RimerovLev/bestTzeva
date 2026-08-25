package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Доменная (бизнес-) валидация не пройдена — данные синтаксически корректны,
 * но нарушают бизнес-правило. HTTP 422 (в отличие от 400 для некорректной формы запроса).
 */
public class ValidationException extends BusinessException {

    public ValidationException(String code, Object... args) {
        super(code, HttpStatus.UNPROCESSABLE_ENTITY, args);
    }
}
