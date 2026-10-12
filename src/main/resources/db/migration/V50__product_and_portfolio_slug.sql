-- B-02: slug товара для индексируемых адресов витрины + история slug'ов для 301-редиректов.
-- Портфолио уже имеет slug; добавляем ему таблицу истории редиректов.

-- 1. Колонка slug у товара (сначала nullable — заполним backfill'ом, затем NOT NULL + unique).
alter table products add column slug varchar(160);

-- 2. Backfill: slug из английского названия (или SKU, если перевода нет), по тем же правилам, что
--    и генерация в коде (нижний регистр, снятие диакритики через unaccent, всё не [a-z0-9] → '-').
update products p
set slug = nullif(trim(both '-' from regexp_replace(
        lower(unaccent(coalesce(
                (select t.name from product_translations t where t.product_id = p.id and t.locale = 'en' limit 1),
                p.sku))),
        '[^a-z0-9]+', '-', 'g')), '');

-- 3. Коллизии: первый оставляем как есть, последующим дописываем -N.
with dup as (
    select id, row_number() over (partition by slug order by id) as rn
    from products
    where slug is not null
)
update products p
set slug = p.slug || '-' || dup.rn
from dup
where dup.id = p.id and dup.rn > 1;

-- 4. Пустые (нет ни перевода, ни ASCII в SKU) — детерминированный запасной slug.
update products set slug = 'product-' || id where slug is null;

alter table products alter column slug set not null;
create unique index uq_products_slug on products (slug);

-- Страховка на инсертах в обход приложения (импорт каталога, сиды): если slug не задан, выводим его
-- из SKU. Приложение всегда задаёт slug само (генерация из английского названия), поэтому для него
-- триггер — no-op. Коллизия разводится коротким случайным суффиксом.
create or replace function products_fill_slug() returns trigger as $$
begin
    if new.slug is null or new.slug = '' then
        new.slug := trim(both '-' from regexp_replace(lower(unaccent(coalesce(new.sku, ''))),
                '[^a-z0-9]+', '-', 'g'));
        if new.slug = '' then
            new.slug := 'product';
        end if;
        if exists (select 1 from products where slug = new.slug) then
            new.slug := new.slug || '-' || left(md5(random()::text), 6);
        end if;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger trg_products_fill_slug
    before insert on products
    for each row execute function products_fill_slug();

-- 5. История slug'ов товара: старый slug → текущий товар. PK по slug — один товар на старый адрес.
create table product_slug_history (
    slug       varchar(160) primary key,
    product_id bigint      not null references products (id) on delete cascade,
    created_at timestamptz not null default now()
);
create index ix_product_slug_history_product on product_slug_history (product_id);

-- 6. То же для портфолио.
create table portfolio_slug_history (
    slug       varchar(160) primary key,
    project_id bigint      not null references portfolio_projects (id) on delete cascade,
    created_at timestamptz not null default now()
);
create index ix_portfolio_slug_history_project on portfolio_slug_history (project_id);
