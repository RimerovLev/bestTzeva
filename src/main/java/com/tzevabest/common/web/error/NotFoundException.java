package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/** Запрошенный ресурс не найден. HTTP 404. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String code, Object... args) {
        super(code, HttpStatus.NOT_FOUND, args);
    }
}
