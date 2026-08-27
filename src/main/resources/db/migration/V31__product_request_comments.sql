-- ─────────────────────────────────────────────────────────────────────────────
-- T-15.2: внутренние комментарии по заявке на товар (лента, видимая только персоналу).
-- ─────────────────────────────────────────────────────────────────────────────

create table product_request_comments (
    id         bigint generated always as identity primary key,
    request_id bigint        not null,
    author_id  bigint,
    text       varchar(2000) not null,
    created_at timestamptz   not null default now(),
    constraint fk_product_request_comments_request foreign key (request_id) references product_requests (id) on delete cascade,
    constraint fk_product_request_comments_author  foreign key (author_id)  references users (id)            on delete set null
);
create index ix_product_request_comments_request on product_request_comments (request_id);
