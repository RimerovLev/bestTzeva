-- ─────────────────────────────────────────────────────────────────────────────
-- T-13.3: ретеншен аудита. audit_log append-only (V22/R-21), но фоновой очистке
-- нужно удалять записи старше срока. Разрешаем DELETE только когда в текущей
-- транзакции выставлен санкционирующий флаг сессии tzevabest.audit_purge='on'
-- (ставит AuditRetentionService). Обычные соединения флага не ставят — журнал
-- по-прежнему нельзя править «тем же соединением, что и данные». consents
-- остаётся строго append-only.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function reject_audit_mutation() returns trigger as $$
begin
    if tg_op = 'DELETE' and current_setting('tzevabest.audit_purge', true) = 'on' then
        return old; -- санкционированная очистка ретеншена
    end if;
    raise exception 'table % is append-only: % is not allowed', tg_table_name, tg_op
        using errcode = '0A000';
end;
$$ language plpgsql;

drop trigger trg_audit_log_append_only on audit_log;
create trigger trg_audit_log_append_only
    before update or delete on audit_log
    for each row execute function reject_audit_mutation();
