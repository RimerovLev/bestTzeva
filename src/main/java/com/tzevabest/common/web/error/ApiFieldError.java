package com.tzevabest.common.web.error;

/**
 * Ошибка по конкретному полю запроса — элемент расширения {@code errors}
 * в теле ответа RFC 9457.
 *
 * @param field   имя поля (или параметра), не прошедшего валидацию
 * @param message локализованное сообщение об ошибке
 */
public record ApiFieldError(String field, String message) {
}
