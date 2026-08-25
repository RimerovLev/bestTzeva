-- ─────────────────────────────────────────────────────────────────────────────
-- Отзывы на товары и услуги (полиморфная цель target_type/target_id, без FK на цель)
-- с модерацией и медиа. Enum/статус — varchar + check.
-- ─────────────────────────────────────────────────────────────────────────────

create table reviews (
    id                    bigint generated always as identity primary key,
    target_type           varchar(16)  not null,
    target_id             bigint       not null,
    user_id               bigint,
    order_id              bigint,
    author_name           varchar(200) not null,
    rating                smallint     not null,
    title                 varchar(255),
    body                  text,
    is_verified_purchase  boolean      not null default false,
    status                varchar(16)  not null default 'PENDING',
    moderated_by          bigint,
    moderated_at          timestamptz,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    constraint fk_reviews_user_id      foreign key (user_id) references users (id) on delete set null,
    constraint fk_reviews_order_id     foreign key (order_id) references orders (id) on delete set null,
    constraint fk_reviews_moderated_by foreign key (moderated_by) references users (id) on delete set null,
    constraint ck_reviews_target_type check (target_type in ('PRODUCT', 'SERVICE')),
    constraint ck_reviews_status      check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    constraint ck_reviews_rating      check (rating between 1 and 5)
);

create index ix_reviews_target on reviews (target_type, target_id, status);

create trigger trg_reviews_set_updated_at before update on reviews for each row execute function set_updated_at();

create table review_media (
    id         bigint generated always as identity primary key,
    review_id  bigint        not null,
    url        varchar(1000) not null,
    created_at timestamptz   not null default now(),
    constraint fk_review_media_review_id foreign key (review_id) references reviews (id) on delete cascade
);

create index ix_review_media_review_id on review_media (review_id);
