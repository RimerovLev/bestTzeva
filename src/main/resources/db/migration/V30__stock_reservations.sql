-- ─────────────────────────────────────────────────────────────────────────────
-- T-5.2: резервирование остатков под заявку на товар. Резерв ставит менеджер при
-- обработке заявки (RESERVED), срок — 3 суток; по истечении заявка уходит в LOST
-- с причиной reservation_expired. Резерв — явная запись с TTL и привязкой к
-- заявке, а не только агрегат inventory.reserved_qty.
-- ─────────────────────────────────────────────────────────────────────────────

create table stock_reservations (
    id           bigint generated always as identity primary key,
    variant_id   bigint      not null,
    warehouse_id bigint      not null,
    request_id   bigint      not null,
    qty          integer     not null,
    status       varchar(16) not null default 'ACTIVE',
    expires_at   timestamptz not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint fk_stock_reservations_variant   foreign key (variant_id)   references product_variants (id) on delete restrict,
    constraint fk_stock_reservations_warehouse foreign key (warehouse_id) references warehouses (id)       on delete restrict,
    constraint fk_stock_reservations_request   foreign key (request_id)   references product_requests (id) on delete cascade,
    constraint ck_stock_reservations_qty    check (qty >= 1),
    constraint ck_stock_reservations_status check (status in ('ACTIVE','RELEASED','EXPIRED'))
);
create index ix_stock_reservations_request on stock_reservations (request_id);
create index ix_stock_reservations_active_expiry on stock_reservations (expires_at) where status = 'ACTIVE';

-- Движения резерва привязываются к заявке: reference_type REQUEST (ORDER мёртв после сноса).
alter table inventory_movements drop constraint ck_inventory_movements_reference_type;
alter table inventory_movements add constraint ck_inventory_movements_reference_type
    check (reference_type is null or reference_type in ('ORDER', 'MANUAL', 'IMPORT', 'REQUEST'));
