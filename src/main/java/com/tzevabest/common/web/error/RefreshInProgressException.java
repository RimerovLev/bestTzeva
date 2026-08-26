package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Параллельная ротация refresh ещё не завершилась (409). Клиенту предлагается повторить запрос через
 * {@code retryAfterSeconds} — к тому времени пара уже будет выдана. Не признак кражи: массовый отзыв
 * не выполняется (R-36).
 */
public class RefreshInProgressException extends BusinessException {

    private final long retryAfterSeconds;

    public RefreshInProgressException(long retryAfterSeconds) {
        super("common.auth.refresh_in_progress", HttpStatus.CONFLICT);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
