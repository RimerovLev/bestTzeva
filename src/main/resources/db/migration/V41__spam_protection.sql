-- ─────────────────────────────────────────────────────────────────────────────
-- T-11.1.1: общая защита публичных форм (заявки на товар и на покраску). Общий
-- журнал отсечённых попыток spam_drops (заменяет request_spam_drops), плюс
-- блок-лист телефонов и IP с админским управлением.
-- ─────────────────────────────────────────────────────────────────────────────

create table spam_drops (
    id         bigint generated always as identity primary key,
    form       varchar(32)  not null,
    ip         varchar(64),
    phone      varchar(32),
    reason     varchar(32)  not null,
    created_at timestamptz  not null default now(),
    constraint ck_spam_drops_reason check (reason in ('honeypot', 'too_fast', 'blocked'))
);
create index ix_spam_drops_created on spam_drops (created_at desc);

-- Переносим прежние отсечения формы товара в общий журнал и убираем частную таблицу.
insert into spam_drops (form, ip, reason, created_at)
    select 'product_request', ip, reason, created_at from request_spam_drops;
drop table request_spam_drops;

create table block_list (
    id         bigint generated always as identity primary key,
    kind       varchar(8)   not null,
    value      varchar(255) not null,
    reason     varchar(255),
    created_by bigint,
    created_at timestamptz  not null default now(),
    constraint ck_block_list_kind check (kind in ('IP', 'PHONE')),
    constraint uq_block_list_kind_value unique (kind, value),
    constraint fk_block_list_created_by foreign key (created_by) references users (id) on delete set null
);
