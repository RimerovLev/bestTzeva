package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Некорректный по форме запрос (например сортировка по недопустимому полю). HTTP 400.
 * В отличие от {@link ValidationException} (422 — данные валидны по форме, но нарушают
 * бизнес-правило), здесь ошибка в самой форме запроса.
 */
public class BadRequestException extends BusinessException {

    public BadRequestException(String code, Object... args) {
        super(code, HttpStatus.BAD_REQUEST, args);
    }
}
