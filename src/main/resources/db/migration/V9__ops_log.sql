-- ─────────────────────────────────────────────────────────────────────────────
-- Операционные журналы: отправленные уведомления, аудит действий из админки и
-- согласия пользователей (израильский закон о защите приватности).
-- audit_log и consents — append-only; actor_user_id в audit хранится без FK, чтобы
-- запись переживала удаление пользователя.
-- ─────────────────────────────────────────────────────────────────────────────

create table notifications_log (
    id                  bigint generated always as identity primary key,
    channel             varchar(16)  not null,
    template_code       varchar(120) not null,
    recipient           varchar(255) not null,
    locale              varchar(5),
    subject             varchar(255),
    body_snapshot       text,
    status              varchar(16)  not null default 'QUEUED',
    provider_message_id varchar(255),
    error_message       text,
    attempt_count       integer      not null default 0,
    sent_at             timestamptz,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    constraint ck_notifications_log_channel check (channel in ('EMAIL', 'SMS', 'WHATSAPP', 'PUSH')),
    constraint ck_notifications_log_status  check (status in ('QUEUED', 'SENT', 'FAILED'))
);

create index ix_notifications_log_status_created on notifications_log (status, created_at);

create trigger trg_notifications_log_set_updated_at before update on notifications_log for each row execute function set_updated_at();

-- Аудит изменений из админки (append-only).
create table audit_log (
    id            bigint generated always as identity primary key,
    actor_user_id bigint,
    actor_ip      varchar(45),
    action        varchar(120) not null,
    entity_type   varchar(120) not null,
    entity_id     bigint,
    changes       jsonb,
    created_at    timestamptz  not null default now()
);

create index ix_audit_log_entity on audit_log (entity_type, entity_id, created_at desc);

-- Согласия пользователей (append-only журнал событий согласия).
create table consents (
    user_id      bigint,
    id           bigint generated always as identity primary key,
    contact      varchar(255) not null,
    consent_type varchar(24)  not null,
    granted      boolean      not null,
    source       varchar(64),
    ip           varchar(45),
    created_at   timestamptz  not null default now(),
    constraint fk_consents_user_id foreign key (user_id) references users (id) on delete set null,
    constraint ck_consents_type check (consent_type in ('MARKETING', 'DATA_PROCESSING'))
);

create index ix_consents_contact on consents (contact);
create index ix_consents_user_id on consents (user_id);
