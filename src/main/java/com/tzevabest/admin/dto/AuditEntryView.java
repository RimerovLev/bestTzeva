package com.tzevabest.admin.dto;

import java.time.Instant;
import java.util.Map;

/** Запись журнала аудита. {@code actorName} — имя сотрудника (или email), null для системных записей. */
public record AuditEntryView(long id, Long actorUserId, String actorName, String actorIp, String action,
                             String entityType, Long entityId, Map<String, Object> changes, Instant createdAt) {
}
