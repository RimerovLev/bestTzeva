-- ─────────────────────────────────────────────────────────────────────────────
-- T-4.3 (денормализация каталога): products.min_price / max_price / has_stock.
-- Поля поддерживаются ТРИГГЕРОМ на product_variants (after insert/update/delete),
-- поэтому массовые изменения цен обычным UPDATE product_variants его не обходят —
-- построчный триггер срабатывает на каждую задетую строку.
--   min_price/max_price — по АКТИВНЫМ вариантам товара (null, если активных нет);
--   has_stock — есть ли активный вариант с доступным остатком на активном складе
--               (через v_variant_available, который уже фильтрует активные склады).
-- Поля не мапятся в JPA-сущность Product: их пишет только триггер, читаются они SQL'ом.
-- ─────────────────────────────────────────────────────────────────────────────

alter table products
    add column min_price numeric(12,2),
    add column max_price numeric(12,2),
    add column has_stock boolean not null default false;

create or replace function refresh_product_denorm(p_product_id bigint) returns void as $$
    update products p set
        min_price = sub.min_price,
        max_price = sub.max_price,
        has_stock = coalesce(sub.has_stock, false)
    from (
        select min(pv.price)                                  as min_price,
               max(pv.price)                                  as max_price,
               bool_or(coalesce(a.available_qty, 0) > 0)      as has_stock
        from product_variants pv
        left join v_variant_available a on a.variant_id = pv.id
        where pv.product_id = p_product_id and pv.is_active
    ) sub
    where p.id = p_product_id;
$$ language sql;

create or replace function trg_variant_refresh_denorm() returns trigger as $$
begin
    if tg_op = 'DELETE' then
        perform refresh_product_denorm(old.product_id);
        return old;
    end if;
    -- смена товара у варианта (редко) — пересчитать и старый, и новый
    if tg_op = 'UPDATE' and new.product_id <> old.product_id then
        perform refresh_product_denorm(old.product_id);
    end if;
    perform refresh_product_denorm(new.product_id);
    return new;
end;
$$ language plpgsql;

create trigger trg_product_variants_denorm
    after insert or update or delete on product_variants
    for each row execute function trg_variant_refresh_denorm();

-- backfill для уже существующих товаров
do $$
declare r record;
begin
    for r in select id from products loop
        perform refresh_product_denorm(r.id);
    end loop;
end $$;
