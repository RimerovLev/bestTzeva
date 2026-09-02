package com.tzevabest.admin.dto;

import java.time.Instant;
import java.util.Map;

/** Запись журнала аудита. */
public record AuditEntryView(long id, Long actorUserId, String actorIp, String action,
                             String entityType, Long entityId, Map<String, Object> changes, Instant createdAt) {
}
