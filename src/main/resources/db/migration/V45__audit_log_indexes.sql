-- T-13.3: индексы под фильтры журнала аудита (автор, действие, период).
-- Индекс по (entity_type, entity_id) уже есть в V9 (ix_audit_log_entity).
create index ix_audit_log_actor   on audit_log (actor_user_id);
create index ix_audit_log_action  on audit_log (action);
create index ix_audit_log_created on audit_log (created_at desc);
