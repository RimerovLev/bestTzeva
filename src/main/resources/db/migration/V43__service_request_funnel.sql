-- ─────────────────────────────────────────────────────────────────────────────
-- T-11.3: воронка обработки заявок на покраску. Внутренние комментарии (лента
-- для персонала), время замера и ключ PDF коммерческого предложения.
-- ─────────────────────────────────────────────────────────────────────────────

create table service_request_comments (
    id         bigint generated always as identity primary key,
    request_id bigint       not null,
    author_id  bigint,
    body       varchar(2000) not null,
    created_at timestamptz  not null default now(),
    constraint fk_service_request_comments_request foreign key (request_id) references service_requests (id) on delete cascade,
    constraint fk_service_request_comments_author  foreign key (author_id)  references users (id) on delete set null
);
create index ix_service_request_comments_request on service_request_comments (request_id, created_at);

alter table service_requests
    add column measurement_at timestamptz,
    add column quote_pdf_key  varchar(512);
