-- ─────────────────────────────────────────────────────────────────────────────
-- R-64: право на удаление должно затрагивать и consents. Запись согласия —
-- доказательство законности обработки, стирать её целиком нельзя, но контакт в
-- ней можно заменить непрозрачным псевдонимом (сохраняя тип согласия, версию
-- политики и время). Для этого нужен контролируемый путь UPDATE.
--
-- По образцу V46 (санкционированный DELETE аудита): UPDATE на consents разрешён
-- только когда в текущей транзакции выставлен флаг tzevabest.consent_anonymize='on'
-- (ставит PrivacyService.anonymize) И меняется ТОЛЬКО колонка contact — остальные
-- поля должны остаться нетронутыми. DELETE и любой другой UPDATE по-прежнему
-- запрещены: журнал согласий остаётся доказательным.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function reject_consents_mutation() returns trigger as $$
begin
    if tg_op = 'UPDATE'
       and current_setting('tzevabest.consent_anonymize', true) = 'on'
       and new.contact is distinct from old.contact          -- контакт действительно меняется
       and new.id = old.id
       and new.user_id is not distinct from old.user_id
       and new.consent_type = old.consent_type
       and new.granted = old.granted
       and new.source is not distinct from old.source
       and new.ip is not distinct from old.ip
       and new.policy_version = old.policy_version
       and new.created_at = old.created_at then
        return new; -- санкционированная псевдонимизация контакта
    end if;
    raise exception 'table % is append-only: % is not allowed', tg_table_name, tg_op
        using errcode = '0A000';
end;
$$ language plpgsql;

drop trigger trg_consents_append_only on consents;
create trigger trg_consents_append_only
    before update or delete on consents
    for each row execute function reject_consents_mutation();
