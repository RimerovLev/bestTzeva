-- ─────────────────────────────────────────────────────────────────────────────
-- T-15.1: заявка на товар (лид, не сделка). Покупка ушла в офлайн: клиент
-- оставляет позиции и контакт, менеджер перезванивает. Ближе к заявке на
-- покраску, чем к бывшему заказу — без оплаты, сумм к оплате и доставки.
-- ─────────────────────────────────────────────────────────────────────────────

create table product_requests (
    id                     bigint generated always as identity primary key,
    request_number         varchar(20)  not null,
    customer_name          varchar(200) not null,
    customer_phone         varchar(32)  not null,
    customer_email         varchar(255),
    comment                text,
    preferred_contact_time varchar(120),
    status                 varchar(24)  not null default 'NEW',
    assigned_manager_id    bigint,
    source                 varchar(16)  not null,
    consent_given_at       timestamptz  not null,
    policy_version         varchar(32)  not null,
    created_at             timestamptz  not null default now(),
    updated_at             timestamptz  not null default now(),
    constraint uq_product_requests_number   unique (request_number),
    constraint fk_product_requests_manager  foreign key (assigned_manager_id) references users (id) on delete set null,
    constraint ck_product_requests_status   check (status in ('NEW','CONTACTED','RESERVED','COMPLETED','CANCELLED','LOST')),
    constraint ck_product_requests_source   check (source in ('SITE_FORM','PHONE','WHATSAPP'))
);
create index ix_product_requests_status_created on product_requests (status, created_at desc);

-- Снимки полей обязательны: изменение каталога не должно менять то, что видел клиент.
create table product_request_items (
    id                     bigint generated always as identity primary key,
    request_id             bigint       not null,
    variant_id             bigint       not null,
    qty                    integer      not null,
    product_name_snapshot  varchar(255) not null,
    sku_snapshot           varchar(64)  not null,
    color_snapshot         varchar(64),
    volume_liters_snapshot numeric(5,2),
    price_snapshot         numeric(12,2) not null,
    created_at             timestamptz  not null default now(),
    constraint fk_product_request_items_request foreign key (request_id) references product_requests (id) on delete cascade,
    constraint fk_product_request_items_variant foreign key (variant_id) references product_variants (id) on delete restrict,
    constraint ck_product_request_items_qty   check (qty >= 1),
    constraint ck_product_request_items_price check (price_snapshot >= 0)
);
create index ix_product_request_items_request on product_request_items (request_id);

create table product_request_history (
    id          bigint generated always as identity primary key,
    request_id  bigint      not null,
    from_status varchar(24),
    to_status   varchar(24) not null,
    changed_by  bigint,
    comment     varchar(500),
    created_at  timestamptz not null default now(),
    constraint fk_product_request_history_request    foreign key (request_id) references product_requests (id) on delete cascade,
    constraint fk_product_request_history_changed_by foreign key (changed_by) references users (id) on delete set null,
    constraint ck_product_request_history_from check (from_status is null or from_status in
        ('NEW','CONTACTED','RESERVED','COMPLETED','CANCELLED','LOST')),
    constraint ck_product_request_history_to check (to_status in
        ('NEW','CONTACTED','RESERVED','COMPLETED','CANCELLED','LOST'))
);
