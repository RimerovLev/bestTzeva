-- ─────────────────────────────────────────────────────────────────────────────
-- Направление услуг покраски: заявки (с фото и журналом), портфолио проектов и
-- правила цен для калькулятора. Отдельная от магазина воронка.
-- Enum/статусы — varchar + check (верхний регистр). work_types — дочерние таблицы.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Заявки на услугу ─────────────────────────────────────────────────────────
create table service_requests (
    id                  bigint generated always as identity primary key,
    request_number      varchar(20)  not null,
    user_id             bigint,
    customer_name       varchar(200) not null,
    customer_phone      varchar(32)  not null,
    customer_email      varchar(255),
    property_type       varchar(16)  not null,
    area_sqm            numeric(8,2),
    rooms_count         integer,
    ceiling_height_m    numeric(3,2),
    city                varchar(120),
    address             text,
    preferred_date_from date,
    preferred_date_to   date,
    comment             text,
    estimated_price_min numeric(12,2),
    estimated_price_max numeric(12,2),
    quoted_price        numeric(12,2),
    status              varchar(24)  not null default 'NEW',
    assigned_manager_id bigint,
    source              varchar(16)  not null,
    consent_given_at    timestamptz,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    constraint uq_service_requests_request_number  unique (request_number),
    constraint fk_service_requests_user_id         foreign key (user_id) references users (id) on delete set null,
    constraint fk_service_requests_manager_id      foreign key (assigned_manager_id) references users (id) on delete set null,
    constraint ck_service_requests_property_type   check (property_type in ('APARTMENT', 'HOUSE', 'OFFICE', 'COMMERCIAL', 'OTHER')),
    constraint ck_service_requests_source          check (source in ('SITE_FORM', 'PHONE', 'WHATSAPP')),
    constraint ck_service_requests_status          check (status in ('NEW', 'CONTACTED', 'MEASUREMENT_SCHEDULED', 'MEASURED',
                                                                     'QUOTED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'LOST'))
);

create index ix_service_requests_status_created on service_requests (status, created_at desc);
create index ix_service_requests_manager_id     on service_requests (assigned_manager_id);
create index ix_service_requests_customer_phone on service_requests (customer_phone);

create trigger trg_service_requests_set_updated_at before update on service_requests for each row execute function set_updated_at();

-- Виды работ по заявке (набор значений).
create table service_request_work_types (
    request_id bigint      not null,
    work_type  varchar(16) not null,
    constraint pk_service_request_work_types primary key (request_id, work_type),
    constraint fk_service_request_work_types_request_id foreign key (request_id) references service_requests (id) on delete cascade,
    constraint ck_service_request_work_types_value check (work_type in ('WALLS', 'CEILING', 'FACADE', 'WOOD', 'METAL', 'DECORATIVE'))
);

-- ── Фото помещения от клиента ────────────────────────────────────────────────
create table service_request_media (
    id          bigint generated always as identity primary key,
    request_id  bigint       not null,
    url         varchar(1000) not null,
    type        varchar(16)  not null,
    uploaded_at timestamptz  not null default now(),
    constraint fk_service_request_media_request_id foreign key (request_id) references service_requests (id) on delete cascade,
    constraint ck_service_request_media_type check (type in ('IMAGE', 'VIDEO', 'DOC'))
);

create index ix_service_request_media_request_id on service_request_media (request_id);

-- ── Журнал смены статусов заявки (append-only) ───────────────────────────────
create table service_request_history (
    id          bigint generated always as identity primary key,
    request_id  bigint      not null,
    from_status varchar(24),
    to_status   varchar(24) not null,
    changed_by  bigint,
    comment     varchar(500),
    created_at  timestamptz not null default now(),
    constraint fk_service_request_history_request_id foreign key (request_id) references service_requests (id) on delete cascade,
    constraint fk_service_request_history_changed_by foreign key (changed_by) references users (id) on delete set null,
    constraint ck_service_request_history_from check (from_status is null or from_status in
        ('NEW', 'CONTACTED', 'MEASUREMENT_SCHEDULED', 'MEASURED', 'QUOTED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'LOST')),
    constraint ck_service_request_history_to check (to_status in
        ('NEW', 'CONTACTED', 'MEASUREMENT_SCHEDULED', 'MEASURED', 'QUOTED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'LOST'))
);

create index ix_service_request_history_request_id on service_request_history (request_id);

-- ── Портфолио ────────────────────────────────────────────────────────────────
create table portfolio_projects (
    id            bigint generated always as identity primary key,
    slug          varchar(140) not null,
    property_type varchar(16),
    city          varchar(120),
    area_sqm      numeric(8,2),
    duration_days integer,
    completed_at  date,
    is_published  boolean     not null default false,
    sort_order    integer     not null default 0,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint uq_portfolio_projects_slug unique (slug),
    constraint ck_portfolio_projects_property_type check (property_type is null or property_type in
        ('APARTMENT', 'HOUSE', 'OFFICE', 'COMMERCIAL', 'OTHER'))
);

create index ix_portfolio_projects_published on portfolio_projects (is_published, sort_order);

create trigger trg_portfolio_projects_set_updated_at before update on portfolio_projects for each row execute function set_updated_at();

create table portfolio_project_work_types (
    project_id bigint      not null,
    work_type  varchar(16) not null,
    constraint pk_portfolio_project_work_types primary key (project_id, work_type),
    constraint fk_portfolio_project_work_types_project_id foreign key (project_id) references portfolio_projects (id) on delete cascade,
    constraint ck_portfolio_project_work_types_value check (work_type in ('WALLS', 'CEILING', 'FACADE', 'WOOD', 'METAL', 'DECORATIVE'))
);

create table portfolio_translations (
    id          bigint generated always as identity primary key,
    project_id  bigint       not null,
    locale      varchar(5)   not null,
    title       varchar(255) not null,
    description text,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint uq_portfolio_translations_project_locale unique (project_id, locale),
    constraint fk_portfolio_translations_project_id foreign key (project_id) references portfolio_projects (id) on delete cascade,
    constraint ck_portfolio_translations_locale check (locale in ('he', 'ru', 'en'))
);

create table portfolio_media (
    id          bigint generated always as identity primary key,
    project_id  bigint        not null,
    url         varchar(1000) not null,
    kind        varchar(16)   not null,
    sort_order  integer       not null default 0,
    pair_group  varchar(64),
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    constraint fk_portfolio_media_project_id foreign key (project_id) references portfolio_projects (id) on delete cascade,
    constraint ck_portfolio_media_kind check (kind in ('BEFORE', 'AFTER', 'PROCESS'))
);

create index ix_portfolio_media_project_id on portfolio_media (project_id);

create trigger trg_portfolio_media_set_updated_at before update on portfolio_media for each row execute function set_updated_at();

-- ── Правила цен для калькулятора ─────────────────────────────────────────────
create table service_pricing_rules (
    id                bigint generated always as identity primary key,
    property_type     varchar(16)  not null,
    work_type         varchar(16)  not null,
    price_per_sqm_min numeric(12,2) not null,
    price_per_sqm_max numeric(12,2) not null,
    min_order_price   numeric(12,2) not null default 0,
    is_active         boolean      not null default true,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    constraint uq_service_pricing_rules_type unique (property_type, work_type),
    constraint ck_service_pricing_rules_property_type check (property_type in ('APARTMENT', 'HOUSE', 'OFFICE', 'COMMERCIAL', 'OTHER')),
    constraint ck_service_pricing_rules_work_type check (work_type in ('WALLS', 'CEILING', 'FACADE', 'WOOD', 'METAL', 'DECORATIVE')),
    constraint ck_service_pricing_rules_price_range check (price_per_sqm_max >= price_per_sqm_min),
    constraint ck_service_pricing_rules_prices check (price_per_sqm_min >= 0 and min_order_price >= 0)
);

create trigger trg_service_pricing_rules_set_updated_at before update on service_pricing_rules for each row execute function set_updated_at();
