-- ─────────────────────────────────────────────────────────────────────────────
-- T-12.2: transactional outbox для уведомлений. Запись в notifications_log в
-- статусе QUEUED делается в той же транзакции, что и бизнес-операция; фоновый
-- обработчик забирает готовые к отправке (next_attempt_at <= now) пачкой с
-- SKIP LOCKED, отправляет и обновляет статус. Ретраи — по next_attempt_at.
-- Дедупликация — уникальный ключ идемпотентности (nulls различны, поэтому
-- немаркированные отправки не конфликтуют).
-- ─────────────────────────────────────────────────────────────────────────────

alter table notifications_log
    add column next_attempt_at timestamptz,
    add column idempotency_key varchar(255),
    add column priority        varchar(16) not null default 'NORMAL';

alter table notifications_log
    add constraint ck_notifications_log_priority check (priority in ('IMMEDIATE', 'NORMAL'));

-- Дедуп: одно уведомление на событие (templateCode:recipient:referenceId).
create unique index uq_notifications_log_idem on notifications_log (idempotency_key);

-- Выборка готовых к отправке.
create index ix_notifications_log_due on notifications_log (status, next_attempt_at);
