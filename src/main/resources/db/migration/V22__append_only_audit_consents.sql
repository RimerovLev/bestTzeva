-- ─────────────────────────────────────────────────────────────────────────────
-- R-21: audit_log и consents объявлены append-only, но на уровне БД ничего не
-- запрещало UPDATE/DELETE. Требование T-13.3 — ограничение в БД, а не только в
-- приложении: журнал, который можно править тем же соединением, что и данные,
-- ничего не доказывает.
-- Триггер before update or delete безусловно поднимает исключение. INSERT
-- разрешён (append-only), поэтому запись согласий и аудита продолжает работать.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function reject_mutation() returns trigger as $$
begin
    raise exception 'table % is append-only: % is not allowed', tg_table_name, tg_op
        using errcode = '0A000'; -- feature_not_supported
end;
$$ language plpgsql;

create trigger trg_audit_log_append_only
    before update or delete on audit_log
    for each row execute function reject_mutation();

create trigger trg_consents_append_only
    before update or delete on consents
    for each row execute function reject_mutation();
