-- ─────────────────────────────────────────────────────────────────────────────
-- Заказы: заказ с позициями (снимки каталога), журнал статусов, платежи с возвратами
-- и отгрузки. Кросс-модульные ссылки (user_id, variant_id, warehouse_id, *_by) — по id.
-- Статусы/провайдеры — varchar + check (верхний регистр). Деньги numeric(12,2).
-- ─────────────────────────────────────────────────────────────────────────────

create table orders (
    id               bigint generated always as identity primary key,
    order_number     varchar(20)  not null,
    user_id          bigint,
    customer_email   varchar(255) not null,
    customer_phone   varchar(32),
    customer_name    varchar(200) not null,
    status           varchar(24)  not null default 'NEW',
    payment_status   varchar(24)  not null default 'PENDING',
    delivery_method  varchar(16)  not null,
    warehouse_id     bigint,
    shipping_address jsonb,
    subtotal         numeric(12,2) not null default 0,
    discount_total   numeric(12,2) not null default 0,
    shipping_total   numeric(12,2) not null default 0,
    vat_amount       numeric(12,2) not null default 0,
    grand_total      numeric(12,2) not null default 0,
    currency         varchar(3)   not null default 'ILS',
    coupon_code      varchar(64),
    customer_note    text,
    admin_note       text,
    placed_at        timestamptz,
    paid_at          timestamptz,
    shipped_at       timestamptz,
    completed_at     timestamptz,
    cancelled_at     timestamptz,
    cancel_reason    varchar(500),
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    constraint uq_orders_order_number      unique (order_number),
    constraint fk_orders_user_id           foreign key (user_id) references users (id) on delete set null,
    constraint fk_orders_warehouse_id      foreign key (warehouse_id) references warehouses (id) on delete set null,
    constraint ck_orders_status            check (status in ('NEW', 'AWAITING_PAYMENT', 'PAID', 'PROCESSING',
                                                             'READY_FOR_PICKUP', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED')),
    constraint ck_orders_payment_status    check (payment_status in ('PENDING', 'AUTHORIZED', 'PAID', 'FAILED',
                                                                     'REFUNDED', 'PARTIALLY_REFUNDED')),
    constraint ck_orders_delivery_method   check (delivery_method in ('PICKUP', 'COURIER')),
    constraint ck_orders_subtotal          check (subtotal >= 0),
    constraint ck_orders_discount_total    check (discount_total >= 0),
    constraint ck_orders_shipping_total    check (shipping_total >= 0),
    constraint ck_orders_vat_amount        check (vat_amount >= 0),
    constraint ck_orders_grand_total       check (grand_total >= 0)
);

create index ix_orders_user_id        on orders (user_id);
create index ix_orders_status_created on orders (status, created_at desc);

create trigger trg_orders_set_updated_at before update on orders for each row execute function set_updated_at();

-- ── Позиции заказа (со снимками каталога) ────────────────────────────────────
create table order_items (
    id                     bigint generated always as identity primary key,
    order_id               bigint       not null,
    variant_id             bigint,
    product_name_snapshot  varchar(255) not null,
    variant_name_snapshot  varchar(255),
    sku_snapshot           varchar(64)  not null,
    color_code_snapshot    varchar(64),
    volume_liters_snapshot numeric(5,2),
    weight_kg_snapshot     numeric(6,3),
    unit_price             numeric(12,2) not null,
    qty                    integer       not null,
    line_total             numeric(12,2) not null,
    created_at             timestamptz  not null default now(),
    updated_at             timestamptz  not null default now(),
    constraint fk_order_items_order_id   foreign key (order_id) references orders (id) on delete cascade,
    constraint fk_order_items_variant_id foreign key (variant_id) references product_variants (id) on delete set null,
    constraint ck_order_items_qty        check (qty >= 1),
    constraint ck_order_items_unit_price check (unit_price >= 0),
    constraint ck_order_items_line_total check (line_total >= 0)
);

create index ix_order_items_order_id on order_items (order_id);

create trigger trg_order_items_set_updated_at before update on order_items for each row execute function set_updated_at();

-- ── Журнал смены статусов (append-only) ──────────────────────────────────────
create table order_status_history (
    id          bigint generated always as identity primary key,
    order_id    bigint      not null,
    from_status varchar(24),
    to_status   varchar(24) not null,
    changed_by  bigint,
    reason      varchar(500),
    created_at  timestamptz not null default now(),
    constraint fk_order_status_history_order_id   foreign key (order_id) references orders (id) on delete cascade,
    constraint fk_order_status_history_changed_by foreign key (changed_by) references users (id) on delete set null,
    constraint ck_order_status_history_from check (from_status is null or from_status in
        ('NEW', 'AWAITING_PAYMENT', 'PAID', 'PROCESSING', 'READY_FOR_PICKUP', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED')),
    constraint ck_order_status_history_to check (to_status in
        ('NEW', 'AWAITING_PAYMENT', 'PAID', 'PROCESSING', 'READY_FOR_PICKUP', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED'))
);

create index ix_order_status_history_order_id on order_status_history (order_id);

-- ── Платежи ──────────────────────────────────────────────────────────────────
create table payments (
    id                      bigint generated always as identity primary key,
    order_id                bigint       not null,
    provider                varchar(16)  not null,
    provider_transaction_id varchar(128),
    status                  varchar(24)  not null,
    amount                  numeric(12,2) not null,
    currency                varchar(3)   not null default 'ILS',
    raw_response            jsonb,
    idempotency_key         varchar(128) not null,
    created_at              timestamptz  not null default now(),
    updated_at              timestamptz  not null default now(),
    constraint uq_payments_idempotency_key unique (idempotency_key),
    constraint fk_payments_order_id foreign key (order_id) references orders (id) on delete restrict,
    constraint ck_payments_provider check (provider in ('TRANZILA', 'CARDCOM', 'GROW', 'MANUAL')),
    constraint ck_payments_status   check (status in ('PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED',
                                                     'REFUNDED', 'PARTIALLY_REFUNDED')),
    constraint ck_payments_amount   check (amount >= 0)
);

create index ix_payments_order_id               on payments (order_id);
create index ix_payments_provider_transaction_id on payments (provider_transaction_id);

create trigger trg_payments_set_updated_at before update on payments for each row execute function set_updated_at();

-- ── Возвраты платежей ────────────────────────────────────────────────────────
create table payment_refunds (
    id                bigint generated always as identity primary key,
    payment_id        bigint       not null,
    amount            numeric(12,2) not null,
    reason            varchar(500),
    provider_refund_id varchar(128),
    status            varchar(24)  not null default 'PENDING',
    created_by        bigint,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    constraint fk_payment_refunds_payment_id foreign key (payment_id) references payments (id) on delete cascade,
    constraint fk_payment_refunds_created_by foreign key (created_by) references users (id) on delete set null,
    constraint ck_payment_refunds_status check (status in ('PENDING', 'SUCCEEDED', 'FAILED')),
    constraint ck_payment_refunds_amount check (amount >= 0)
);

create index ix_payment_refunds_payment_id on payment_refunds (payment_id);

create trigger trg_payment_refunds_set_updated_at before update on payment_refunds for each row execute function set_updated_at();

-- ── Отгрузки ─────────────────────────────────────────────────────────────────
create table shipments (
    id              bigint generated always as identity primary key,
    order_id        bigint      not null,
    carrier         varchar(120),
    tracking_number varchar(128),
    status          varchar(24) not null default 'PENDING',
    shipped_at      timestamptz,
    delivered_at    timestamptz,
    notes           varchar(1000),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint fk_shipments_order_id foreign key (order_id) references orders (id) on delete cascade,
    constraint ck_shipments_status check (status in ('PENDING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'RETURNED', 'CANCELLED'))
);

create index ix_shipments_order_id on shipments (order_id);

create trigger trg_shipments_set_updated_at before update on shipments for each row execute function set_updated_at();
