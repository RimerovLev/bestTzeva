-- Общая триггерная функция: проставляет updated_at при обновлении строки.
create or replace function set_updated_at() returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

-- Пользователи.
create table users (
    id                bigint generated always as identity primary key,
    email             varchar(255) not null,
    phone             varchar(32),
    password_hash     varchar(255) not null,
    first_name        varchar(100) not null,
    last_name         varchar(100) not null,
    role              varchar(32)  not null,
    status            varchar(32)  not null default 'PENDING',
    email_verified_at timestamptz,
    phone_verified_at timestamptz,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    constraint uq_users_phone  unique (phone),
    constraint ck_users_role   check (role in ('CUSTOMER', 'SPECIALIST', 'ADMIN')),
    constraint ck_users_status check (status in ('ACTIVE', 'PENDING', 'BLOCKED'))
);

-- Регистронезависимая уникальность email + индекс поиска по email.
create unique index ux_users_email_lower on users (lower(email));
-- Индекс поиска по phone обеспечивает ограничение uq_users_phone.

-- Адреса пользователей.
create table user_addresses (
    id         bigint generated always as identity primary key,
    user_id    bigint       not null,
    city       varchar(120) not null,
    street     varchar(200) not null,
    house      varchar(32)  not null,
    apartment  varchar(32),
    floor      varchar(16),
    entrance   varchar(16),
    zip        varchar(16),
    notes      varchar(500),
    is_default boolean      not null default false,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint fk_user_addresses_user_id foreign key (user_id) references users (id) on delete cascade
);

create index ix_user_addresses_user_id on user_addresses (user_id);

-- Триггеры автоматического updated_at.
create trigger trg_users_set_updated_at
    before update on users
    for each row execute function set_updated_at();

create trigger trg_user_addresses_set_updated_at
    before update on user_addresses
    for each row execute function set_updated_at();
