package com.tzevabest.admin.service;

import com.tzevabest.admin.domain.AuditLog;
import com.tzevabest.admin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Публичный сервис записи аудита изменений из админки (владелец таблицы audit_log). */
@Service
@RequiredArgsConstructor
public class AuditService {

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
        entry.setChanges(changes);
        auditLog.save(entry);
    }
}
