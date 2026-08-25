-- ─────────────────────────────────────────────────────────────────────────────
-- Купоны и их использование. applies_to_ids — массив bigint (id категорий/товаров/
-- брендов в зависимости от applies_to). Enum/тип — varchar + check (верхний регистр).
-- ─────────────────────────────────────────────────────────────────────────────

create table coupons (
    id                    bigint generated always as identity primary key,
    code                  varchar(64)  not null,
    type                  varchar(16)  not null,
    value                 numeric(12,2) not null default 0,
    min_order_amount      numeric(12,2),
    max_discount_amount   numeric(12,2),
    starts_at             timestamptz,
    expires_at            timestamptz,
    usage_limit_total     integer,
    usage_limit_per_user  integer,
    used_count            integer      not null default 0,
    applies_to            varchar(16)  not null default 'ALL',
    applies_to_ids        bigint[],
    is_active             boolean      not null default true,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    constraint uq_coupons_code       unique (code),
    constraint ck_coupons_type       check (type in ('PERCENT', 'FIXED_AMOUNT', 'FREE_SHIPPING')),
    constraint ck_coupons_applies_to check (applies_to in ('ALL', 'CATEGORY', 'PRODUCT', 'BRAND')),
    constraint ck_coupons_value      check (value >= 0),
    constraint ck_coupons_used_count check (used_count >= 0)
);

create trigger trg_coupons_set_updated_at before update on coupons for each row execute function set_updated_at();

create table coupon_usages (
    id               bigint generated always as identity primary key,
    coupon_id        bigint       not null,
    order_id         bigint       not null,
    user_id          bigint,
    discount_applied numeric(12,2) not null,
    created_at       timestamptz  not null default now(),
    constraint uq_coupon_usages_coupon_order unique (coupon_id, order_id),
    constraint fk_coupon_usages_coupon_id foreign key (coupon_id) references coupons (id) on delete cascade,
    constraint fk_coupon_usages_order_id  foreign key (order_id) references orders (id) on delete cascade,
    constraint fk_coupon_usages_user_id   foreign key (user_id) references users (id) on delete set null,
    constraint ck_coupon_usages_discount check (discount_applied >= 0)
);

create index ix_coupon_usages_coupon_id on coupon_usages (coupon_id);
create index ix_coupon_usages_order_id  on coupon_usages (order_id);
