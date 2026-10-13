package com.tzevabest.admin.service;

import com.tzevabest.admin.dto.BlockListCreate;
import com.tzevabest.admin.dto.BlockListEntry;
import com.tzevabest.common.util.PhoneNormalizer;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Админ-управление блок-листом IP/телефонов (T-11.1.1). Изменения — в audit_log. */
@Service
@RequiredArgsConstructor
public class BlockListAdminService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public List<BlockListEntry> list() {
        return jdbc.query("select id, kind, value, reason, created_at from block_list order by created_at desc",
                (rs, n) -> new BlockListEntry(rs.getLong("id"), rs.getString("kind"), rs.getString("value"),
                        rs.getString("reason"), rs.getTimestamp("created_at").toInstant()));
    }

    @Transactional
    public BlockListEntry add(BlockListCreate req) {
        Long actor = currentUser.optional().map(p -> p.userId()).orElse(null);
        // SpamGuard сверяет нормализованный номер заявки (E.164) — так же храним и запись блок-листа,
        // иначе номер, введённый как 050-…, никого бы не блокировал.
        String value = "PHONE".equals(req.kind()) ? PhoneNormalizer.normalize(req.value()) : req.value().trim();
        Long id;
        try {
            id = jdbc.queryForObject("""
                    insert into block_list (kind, value, reason, created_by) values (?, ?, ?, ?) returning id""",
                    Long.class, req.kind(), value, req.reason(), actor);
        } catch (DuplicateKeyException e) {
            throw new com.tzevabest.common.web.error.ConflictException("spam.block_list.duplicate", value);
        }
        audit.record(actor, currentUser.ip(), "block_list.add", "block_list", id,
                Map.of("kind", req.kind(), "value", value));
        return list().stream().filter(e -> e.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    public void remove(long id) {
        int deleted = jdbc.update("delete from block_list where id = ?", id);
        if (deleted == 0) {
            throw new NotFoundException("spam.block_list.not_found");
        }
        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                "block_list.remove", "block_list", id, Map.of());
    }
}
