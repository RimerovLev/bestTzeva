package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Сбой при обращении к внешней системе (платёжный шлюз, курьерская служба и т.п.).
 * HTTP 502. Детали причины уходят только в лог — наружу отдаётся общий текст и trace_id.
 */
public class ExternalServiceException extends BusinessException {

    public ExternalServiceException(String code, Object... args) {
        super(code, HttpStatus.BAD_GATEWAY, args);
    }

    public ExternalServiceException(String code, Throwable cause) {
        super(code, HttpStatus.BAD_GATEWAY, null, cause);
    }
}
