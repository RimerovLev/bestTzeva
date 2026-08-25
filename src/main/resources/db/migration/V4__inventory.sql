-- ─────────────────────────────────────────────────────────────────────────────
-- Складской учёт: склады, остатки по варианту×складу (с резервом и optimistic lock)
-- и неизменяемый журнал движений. Логика резервирования — в сервисном слое (E6);
-- прямые UPDATE qty из других модулей запрещены.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Склады ───────────────────────────────────────────────────────────────────
create table warehouses (
    id                bigint generated always as identity primary key,
    code              varchar(64) not null,
    name_translations jsonb,
    address           text,
    is_pickup_point   boolean     not null default false,
    is_active         boolean     not null default true,
    sort_order        integer     not null default 0,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint uq_warehouses_code unique (code)
);

-- ── Остатки (текущее состояние) ──────────────────────────────────────────────
create table inventory (
    id                  bigint generated always as identity primary key,
    variant_id          bigint      not null,
    warehouse_id        bigint      not null,
    qty                 integer     not null default 0,
    reserved_qty        integer     not null default 0,
    low_stock_threshold integer,
    version             bigint      not null default 0,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint uq_inventory_variant_warehouse unique (variant_id, warehouse_id),
    constraint fk_inventory_variant_id   foreign key (variant_id) references product_variants (id) on delete cascade,
    constraint fk_inventory_warehouse_id foreign key (warehouse_id) references warehouses (id) on delete restrict,
    constraint ck_inventory_qty              check (qty >= 0),
    constraint ck_inventory_reserved_qty     check (reserved_qty >= 0),
    constraint ck_inventory_reserved_le_qty  check (reserved_qty <= qty)
);

create index ix_inventory_variant_id on inventory (variant_id);

create trigger trg_inventory_set_updated_at
    before update on inventory
    for each row execute function set_updated_at();

-- ── Журнал движений (только вставка) ─────────────────────────────────────────
create table inventory_movements (
    id             bigint generated always as identity primary key,
    variant_id     bigint      not null,
    warehouse_id   bigint      not null,
    type           varchar(24) not null,
    qty_delta      integer     not null,
    qty_after      integer     not null,
    reference_type varchar(16),
    reference_id   bigint,
    comment        varchar(500),
    created_by     bigint,
    created_at     timestamptz not null default now(),
    constraint fk_inventory_movements_variant_id   foreign key (variant_id) references product_variants (id) on delete restrict,
    constraint fk_inventory_movements_warehouse_id foreign key (warehouse_id) references warehouses (id) on delete restrict,
    constraint fk_inventory_movements_created_by   foreign key (created_by) references users (id) on delete set null,
    constraint ck_inventory_movements_type check (type in (
        'RECEIPT', 'SALE', 'RESERVATION', 'RESERVATION_RELEASE',
        'WRITE_OFF', 'STOCKTAKE', 'TRANSFER_IN', 'TRANSFER_OUT')),
    constraint ck_inventory_movements_reference_type check (
        reference_type is null or reference_type in ('ORDER', 'MANUAL', 'IMPORT')),
    constraint ck_inventory_movements_qty_after check (qty_after >= 0)
);

create index ix_inventory_movements_variant_created on inventory_movements (variant_id, created_at desc);
create index ix_inventory_movements_reference       on inventory_movements (reference_type, reference_id);

-- ── Доступное количество (available = qty - reserved) ─────────────────────────
-- По варианту×складу:
create view v_inventory_available as
select variant_id,
       warehouse_id,
       qty,
       reserved_qty,
       (qty - reserved_qty) as available_qty
from inventory;

-- Суммарно по варианту (по всем складам):
create view v_variant_available as
select variant_id,
       sum(qty)::int                  as total_qty,
       sum(reserved_qty)::int         as total_reserved,
       sum(qty - reserved_qty)::int   as available_qty
from inventory
group by variant_id;
