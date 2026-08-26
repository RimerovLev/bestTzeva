package com.tzevabest.common.web.ratelimit;

import com.tzevabest.common.web.error.TooManyRequestsException;
import com.tzevabest.common.web.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Аспект, применяющий {@link RateLimit}. Каждый лимит проверяется в своём окне; при переполнении —
 * {@link TooManyRequestsException} (429 + Retry-After). Лимит с недоступным ключом (напр. user для
 * анонима) пропускается.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiter limiter;
    private final ClientIpResolver ipResolver;
    private final CurrentUser currentUser;

    @Around("@annotation(com.tzevabest.common.web.ratelimit.RateLimit) "
            + "|| @annotation(com.tzevabest.common.web.ratelimit.RateLimits)")
    public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RateLimit[] limits = method.getAnnotationsByType(RateLimit.class);
        HttpServletRequest request = currentRequest();
        String prefix = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        // Ключи FAILURE-лимитов, которые прошли проверку: слот по ним занимаем только если вызов бросит.
        List<FailureLimit> failureLimits = new ArrayList<>();

        for (RateLimit limit : limits) {
            String dimension = resolveDimension(limit.key(), request, joinPoint.getArgs());
            if (dimension == null) {
                continue;
            }
            String key = prefix + ":" + limit.key() + ":" + dimension;
            Duration window = Duration.ofSeconds(limit.windowSeconds());
            RateLimiter.Result result = (limit.countOn() == CountOn.FAILURE)
                    ? limiter.check(key, limit.limit(), window)
                    : limiter.tryAcquire(key, limit.limit(), window);
            if (!result.allowed()) {
                throw new TooManyRequestsException("common.rate_limited", result.retryAfterSeconds());
            }
            if (limit.countOn() == CountOn.FAILURE) {
                failureLimits.add(new FailureLimit(key, window, limit.failureOn()));
            }
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable failure) {
            // Бюджет FAILURE-лимита тратит только «учитываемое» исключение (по умолчанию клиентская
            // BusinessException). Инфраструктурный сбой (БД/Redis) бюджет не трогает (R-37).
            for (FailureLimit failureLimit : failureLimits) {
                if (failureLimit.matches(failure)) {
                    limiter.record(failureLimit.key(), failureLimit.window());
                }
            }
            throw failure;
        }
    }

    private record FailureLimit(String key, Duration window, Class<? extends Throwable>[] failureOn) {
        boolean matches(Throwable failure) {
            for (Class<? extends Throwable> type : failureOn) {
                if (type.isInstance(failure)) {
                    return true;
                }
            }
            return false;
        }
    }

    private String resolveDimension(String key, HttpServletRequest request, Object[] args) {
        if ("ip".equals(key)) {
            return ipResolver.resolve(request);
        }
        if ("user".equals(key)) {
            return currentUser.isAuthenticated() ? Long.toString(currentUser.id()) : null;
        }
        if (key.startsWith("field:")) {
            return fieldValue(key.substring("field:".length()), args);
        }
        return null;
    }

    private static String fieldValue(String name, Object[] args) {
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            RecordComponent[] components = arg.getClass().getRecordComponents();
            if (components == null) {
                continue;
            }
            for (RecordComponent component : components) {
                if (component.getName().equals(name)) {
                    try {
                        Object value = component.getAccessor().invoke(arg);
                        return value == null ? null : value.toString().trim().toLowerCase(Locale.ROOT);
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
