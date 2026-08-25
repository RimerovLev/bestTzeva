package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/** Конфликт с текущим состоянием ресурса (например дубликат). HTTP 409. */
public class ConflictException extends BusinessException {

    public ConflictException(String code, Object... args) {
        super(code, HttpStatus.CONFLICT, args);
    }
}
