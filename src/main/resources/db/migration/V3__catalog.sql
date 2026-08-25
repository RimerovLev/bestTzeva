-- ─────────────────────────────────────────────────────────────────────────────
-- Каталог: категории (дерево до 3 уровней), бренды, товары и их варианты
-- (цвет + объём), переводы, медиа, справочник цветов и EAV-атрибуты.
-- Enum хранится как varchar (имя Java-константы, верхний регистр) + check.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Категории ────────────────────────────────────────────────────────────────
create table categories (
    id         bigint generated always as identity primary key,
    parent_id  bigint,
    slug       varchar(140) not null,
    sort_order integer      not null default 0,
    is_active  boolean      not null default true,
    image_url  varchar(500),
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint uq_categories_slug      unique (slug),
    constraint fk_categories_parent_id foreign key (parent_id) references categories (id) on delete restrict
);

create index ix_categories_parent_id on categories (parent_id);

-- Ограничение глубины дерева: не более 3 уровней.
create or replace function enforce_category_max_depth() returns trigger as $$
declare
    depth  int := 1;
    parent bigint := new.parent_id;
begin
    while parent is not null loop
        depth := depth + 1;
        if depth > 3 then
            raise exception 'category tree depth exceeds 3 levels (category slug=%)', new.slug;
        end if;
        select parent_id into parent from categories where id = parent;
    end loop;
    return new;
end;
$$ language plpgsql;

create trigger trg_categories_max_depth
    before insert or update of parent_id on categories
    for each row execute function enforce_category_max_depth();

create table category_translations (
    id               bigint generated always as identity primary key,
    category_id      bigint      not null,
    locale           varchar(5)  not null,
    name             varchar(255) not null,
    description      text,
    meta_title       varchar(255),
    meta_description varchar(500),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint uq_category_translations_category_locale unique (category_id, locale),
    constraint fk_category_translations_category_id foreign key (category_id) references categories (id) on delete cascade,
    constraint ck_category_translations_locale check (locale in ('he', 'ru', 'en'))
);

-- ── Бренды ───────────────────────────────────────────────────────────────────
create table brands (
    id         bigint generated always as identity primary key,
    slug       varchar(140) not null,
    name       varchar(255) not null,
    logo_url   varchar(500),
    is_active  boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint uq_brands_slug unique (slug)
);

-- ── Товары ───────────────────────────────────────────────────────────────────
create table products (
    id                     bigint generated always as identity primary key,
    category_id            bigint       not null,
    brand_id               bigint       not null,
    sku                    varchar(64)  not null,
    paint_type             varchar(16)  not null,
    usage_area             varchar(16)  not null,
    finish                 varchar(16)  not null,
    base_type              varchar(16)  not null,
    coverage_sqm_per_liter numeric(6,2),
    drying_time_hours      numeric(5,2),
    is_active              boolean      not null default true,
    is_featured            boolean      not null default false,
    rating_avg             numeric(2,1) not null default 0.0,
    rating_count           integer      not null default 0,
    created_at             timestamptz  not null default now(),
    updated_at             timestamptz  not null default now(),
    constraint uq_products_sku          unique (sku),
    constraint fk_products_category_id  foreign key (category_id) references categories (id) on delete restrict,
    constraint fk_products_brand_id     foreign key (brand_id) references brands (id) on delete restrict,
    constraint ck_products_paint_type   check (paint_type in ('INTERIOR', 'EXTERIOR', 'WOOD', 'METAL', 'PRIMER', 'ENAMEL', 'OTHER')),
    constraint ck_products_usage_area   check (usage_area in ('INDOOR', 'OUTDOOR', 'BOTH')),
    constraint ck_products_finish       check (finish in ('MATTE', 'SATIN', 'GLOSS', 'SEMI_GLOSS', 'TEXTURED')),
    constraint ck_products_base_type    check (base_type in ('WATER', 'SOLVENT', 'EPOXY')),
    constraint ck_products_rating_avg   check (rating_avg >= 0.0 and rating_avg <= 5.0),
    constraint ck_products_rating_count check (rating_count >= 0)
);

create index ix_products_category_id       on products (category_id);
create index ix_products_active_paint_type on products (is_active, paint_type);

create table product_translations (
    id                 bigint generated always as identity primary key,
    product_id         bigint      not null,
    locale             varchar(5)  not null,
    name               varchar(255) not null,
    short_description  varchar(500),
    description        text,
    usage_instructions text,
    meta_title         varchar(255),
    meta_description   varchar(500),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint uq_product_translations_product_locale unique (product_id, locale),
    constraint fk_product_translations_product_id foreign key (product_id) references products (id) on delete cascade,
    constraint ck_product_translations_locale check (locale in ('he', 'ru', 'en'))
);

-- Поиск по названию товара: триграммный GIN-индекс (pg_trgm).
create index ix_product_translations_name_trgm on product_translations using gin (name gin_trgm_ops);

-- ── Варианты товара (покупаемая единица: цвет + объём) ───────────────────────
create table product_variants (
    id            bigint generated always as identity primary key,
    product_id    bigint        not null,
    sku           varchar(64)   not null,
    color_code    varchar(64)   not null,
    color_hex     varchar(9),
    volume_liters numeric(5,2)  not null,
    weight_kg     numeric(6,3),
    price         numeric(12,2) not null,
    old_price     numeric(12,2),
    barcode       varchar(64),
    is_active     boolean       not null default true,
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now(),
    constraint uq_product_variants_sku unique (sku),
    constraint uq_product_variants_product_color_volume unique (product_id, color_code, volume_liters),
    constraint fk_product_variants_product_id foreign key (product_id) references products (id) on delete cascade,
    constraint ck_product_variants_price     check (price >= 0),
    constraint ck_product_variants_old_price check (old_price is null or old_price >= 0)
);

create index ix_product_variants_product_id on product_variants (product_id);
create index ix_product_variants_price      on product_variants (price);

-- ── Медиа (принадлежит ровно товару ИЛИ варианту) ────────────────────────────
create table product_media (
    id               bigint generated always as identity primary key,
    product_id       bigint,
    variant_id       bigint,
    url              varchar(1000) not null,
    alt_translations jsonb,
    type             varchar(16)   not null,
    sort_order       integer       not null default 0,
    is_primary       boolean       not null default false,
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now(),
    constraint fk_product_media_product_id foreign key (product_id) references products (id) on delete cascade,
    constraint fk_product_media_variant_id foreign key (variant_id) references product_variants (id) on delete cascade,
    constraint ck_product_media_type  check (type in ('IMAGE', 'VIDEO', 'DOC')),
    -- Ровно один из product_id / variant_id (XOR для boolean).
    constraint ck_product_media_owner check ((product_id is not null) <> (variant_id is not null))
);

create index ix_product_media_product_id on product_media (product_id);
create index ix_product_media_variant_id on product_media (variant_id);

-- ── Справочник цветов ────────────────────────────────────────────────────────
create table color_swatches (
    id                bigint generated always as identity primary key,
    code              varchar(64) not null,
    system            varchar(16) not null,
    hex               varchar(9),
    name_translations jsonb,
    collection        varchar(140),
    is_popular        boolean     not null default false,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint uq_color_swatches_code unique (code),
    constraint ck_color_swatches_system check (system in ('RAL', 'NCS', 'INTERNAL'))
);

create index ix_color_swatches_system on color_swatches (system);

-- ── Дополнительные характеристики (EAV, без изменения схемы) ──────────────────
create table product_attributes (
    id                bigint generated always as identity primary key,
    code              varchar(64) not null,
    name_translations jsonb,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint uq_product_attributes_code unique (code)
);

create table product_attribute_values (
    id           bigint generated always as identity primary key,
    product_id   bigint       not null,
    attribute_id bigint       not null,
    value        varchar(500) not null,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    constraint uq_product_attribute_values_product_attr unique (product_id, attribute_id),
    constraint fk_product_attribute_values_product_id   foreign key (product_id) references products (id) on delete cascade,
    constraint fk_product_attribute_values_attribute_id foreign key (attribute_id) references product_attributes (id) on delete cascade
);

create index ix_product_attribute_values_product_id on product_attribute_values (product_id);

-- ── Триггеры updated_at на все таблицы каталога ──────────────────────────────
create trigger trg_categories_set_updated_at             before update on categories             for each row execute function set_updated_at();
create trigger trg_category_translations_set_updated_at  before update on category_translations  for each row execute function set_updated_at();
create trigger trg_brands_set_updated_at                 before update on brands                 for each row execute function set_updated_at();
create trigger trg_products_set_updated_at               before update on products               for each row execute function set_updated_at();
create trigger trg_product_translations_set_updated_at   before update on product_translations   for each row execute function set_updated_at();
create trigger trg_product_variants_set_updated_at       before update on product_variants       for each row execute function set_updated_at();
create trigger trg_product_media_set_updated_at          before update on product_media          for each row execute function set_updated_at();
create trigger trg_color_swatches_set_updated_at         before update on color_swatches         for each row execute function set_updated_at();
create trigger trg_product_attributes_set_updated_at     before update on product_attributes     for each row execute function set_updated_at();
create trigger trg_product_attribute_values_set_updated_at before update on product_attribute_values for each row execute function set_updated_at();
