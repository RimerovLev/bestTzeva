package com.tzevabest.admin.service;

import com.tzevabest.common.web.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Аспект автоматического аудита (T-13.3): на успешном выполнении {@link Audited}-метода пишет запись
 * в audit_log. Автор и реальный IP — из {@link CurrentUser} (IP проставлен ClientIpResolver, R-08).
 * Чувствительные поля вырезаются в {@link AuditService}.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService audit;
    private final CurrentUser currentUser;
    private final ParameterNameDiscoverer paramNames = new DefaultParameterNameDiscoverer();

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void record(JoinPoint jp, Audited audited, Object result) {
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        String action = audited.action().isBlank() ? audited.entity() + "." + method.getName() : audited.action();

        Object[] args = jp.getArgs();
        String[] names = paramNames.getParameterNames(method);
        Long entityId = null;
        Map<String, Object> changes = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String name = names != null ? names[i] : "arg" + i;
            if (entityId == null && arg instanceof Long l && ("id".equals(name) || name.endsWith("Id"))) {
                entityId = l;
            } else {
                changes.put(name, arg);
            }
        }
        if (entityId == null) {
            entityId = extractId(result);
        }
        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                action, audited.entity(), entityId, changes);
    }

    /** Достаёт id из результата (record-accessor id() или getId()). */
    private static Long extractId(Object result) {
        if (result == null) {
            return null;
        }
        for (String accessor : new String[]{"id", "getId"}) {
            try {
                Object value = result.getClass().getMethod(accessor).invoke(result);
                if (value instanceof Number num) {
                    return num.longValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // нет такого метода — пробуем следующий
            }
        }
        return null;
    }
}
