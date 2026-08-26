package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/** 429 с информацией о времени до повтора (заголовок Retry-After). */
public class TooManyRequestsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String code, long retryAfterSeconds) {
        super(code, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
