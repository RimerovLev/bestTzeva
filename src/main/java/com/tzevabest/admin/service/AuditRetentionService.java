package com.tzevabest.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Ретеншен аудита (T-13.3): записи старше срока удаляются фоновой задачей под распределённой
 * блокировкой (как уборка резервов по R-42). Срок — из конфигурации.
 */
@Service
@RequiredArgsConstructor
public class AuditRetentionService {

    private static final String LOCK_KEY = "audit:retention:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    @Value("${app.audit.retention:P730D}")
    private Duration retention;

    /**
     * Удаляет записи старше срока под распределённой блокировкой. DELETE санкционируется флагом
     * сессии в той же транзакции (audit_log append-only, V46) — set local и delete идут по одному
     * соединению благодаря {@code @Transactional}.
     */
    @Transactional
    public int purge() {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return 0;
        }
        try {
            jdbc.execute("set local tzevabest.audit_purge = 'on'");
            long seconds = retention.getSeconds();
            return jdbc.update(
                    "delete from audit_log where created_at < now() - (? || ' seconds')::interval", seconds);
        } finally {
            redis.delete(LOCK_KEY);
        }
    }
}
