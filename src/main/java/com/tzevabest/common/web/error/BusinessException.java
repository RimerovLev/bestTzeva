package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Базовое бизнес-исключение приложения.
 * <p>
 * Несёт машиночитаемый {@code code} (например {@code catalog.product.not_found}),
 * HTTP-{@code status} и {@code args} для подстановки в локализованное сообщение.
 * {@code code} используется одновременно как ключ ресурсного бандла и как значение
 * расширения {@code code} в теле ответа RFC 9457.
 */
public class BusinessException extends RuntimeException {

    private static final Object[] EMPTY = new Object[0];

    private final String code;
    private final HttpStatus status;
    private final transient Object[] args;

    protected BusinessException(String code, HttpStatus status, Object[] args, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.status = status;
        this.args = (args != null) ? args.clone() : EMPTY;
    }

    public BusinessException(String code, HttpStatus status, Object... args) {
        this(code, status, args, null);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object[] getArgs() {
        return args.clone();
    }
}
