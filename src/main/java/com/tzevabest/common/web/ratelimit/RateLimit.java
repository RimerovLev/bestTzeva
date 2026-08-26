package com.tzevabest.common.web.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ограничение частоты вызова эндпоинта. Ключ:
 * <ul>
 *   <li>{@code "ip"} — по IP клиента (X-Forwarded-For с доверием прокси);</li>
 *   <li>{@code "user"} — по id текущего пользователя (пропускается для анонимных);</li>
 *   <li>{@code "field:<name>"} — по значению поля запроса (компонента record-DTO).</li>
 * </ul>
 * Повторяемая: на одном методе допускается несколько лимитов (напр. IP + email).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {
    String key();

    int limit();

    long windowSeconds();
}
