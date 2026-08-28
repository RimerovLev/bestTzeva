-- ─────────────────────────────────────────────────────────────────────────────
-- R-53: локаль заявки не сохранялась (в событие уходил литерал "he"), поэтому
-- подтверждение уходило на иврите независимо от языка клиента. Храним язык,
-- на котором клиент смотрел витрину.
-- R-51: заполненный honeypot раньше отвечал 422 request.spam — бот сразу видел,
-- что пойман. Теперь заявка молча отбрасывается (201), а факт отсечения пишется
-- сюда для анализа, не касаясь product_requests.
-- ─────────────────────────────────────────────────────────────────────────────

alter table product_requests
    add column locale varchar(2) not null default 'he'
    constraint ck_product_requests_locale check (locale in ('he', 'ru', 'en'));

create table request_spam_drops (
    id         bigint generated always as identity primary key,
    ip         varchar(64),
    reason     varchar(32)  not null default 'honeypot',
    created_at timestamptz  not null default now()
);
create index ix_request_spam_drops_created on request_spam_drops (created_at desc);
