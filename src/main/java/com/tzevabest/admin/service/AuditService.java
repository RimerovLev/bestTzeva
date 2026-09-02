package com.tzevabest.admin.service;

import com.tzevabest.admin.domain.AuditLog;
import com.tzevabest.admin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Публичный сервис записи аудита изменений из админки (владелец таблицы audit_log). */
@Service
@RequiredArgsConstructor
public class AuditService {

    /** Чувствительные поля исключаются из diff (T-13.3): пароли, токены, секреты. */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(password|token|secret|otp|hash|credential).*");
    /** Телефоны клиентов не пишутся целиком — маскируются до последних 4 цифр. */
    private static final Pattern PHONE = Pattern.compile("(?i).*phone.*");
    /** Email клиентов не пишется целиком — маскируется до первой буквы и домена (R-59). */
    private static final Pattern EMAIL = Pattern.compile("(?i).*e[-_]?mail.*");

    private final AuditLogRepository auditLog;

    @Transactional
    public void record(Long actorUserId, String actorIp, String action,
                       String entityType, Long entityId, Map<String, Object> changes) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actorUserId);
        entry.setActorIp(actorIp);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setChanges(sanitize(changes));
        auditLog.save(entry);
    }

    /** Убирает секреты и маскирует телефоны в записи аудита. */
    static Map<String, Object> sanitize(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return changes;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : changes.entrySet()) {
            String key = e.getKey();
            if (SENSITIVE.matcher(key).matches()) {
                continue; // секрет — не пишем вовсе
            }
            if (PHONE.matcher(key).matches()) {
                clean.put(key, maskPhone(String.valueOf(e.getValue())));
            } else if (EMAIL.matcher(key).matches()) {
                clean.put(key, maskEmail(String.valueOf(e.getValue())));
            } else {
                clean.put(key, e.getValue());
            }
        }
        return clean;
    }

    private static String maskPhone(String value) {
        if (value == null || value.length() <= 4 || "null".equals(value)) {
            return "***";
        }
        return "***" + value.substring(value.length() - 4).toLowerCase(Locale.ROOT);
    }

    /** Первая буква локальной части + домен: {@code ivan@example.com → i***@example.com}. */
    private static String maskEmail(String value) {
        if (value == null || "null".equals(value)) {
            return "***";
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***"; // не похоже на email — не рискуем
        }
        return value.charAt(0) + "***" + value.substring(at);
    }
}
