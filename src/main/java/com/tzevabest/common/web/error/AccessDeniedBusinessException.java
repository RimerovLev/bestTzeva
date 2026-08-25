package com.tzevabest.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Доменный отказ в доступе (аутентифицированный пользователь не владеет ресурсом
 * или не имеет прав на операцию по бизнес-правилу). HTTP 403.
 * <p>
 * Отличается от {@link org.springframework.security.access.AccessDeniedException},
 * который выбрасывает Spring Security на уровне фильтров/аннотаций.
 */
public class AccessDeniedBusinessException extends BusinessException {

    public AccessDeniedBusinessException(String code, Object... args) {
        super(code, HttpStatus.FORBIDDEN, args);
    }
}
