-- ─────────────────────────────────────────────────────────────────────────────
-- R-40 + R-49: денормализация products.min_price/max_price/has_stock.
--
-- R-40 (крит): has_stock зависит от v_variant_available (inventory.qty/reserved_qty),
--   но триггер был только на product_variants. Приход/списание/резерв/продажа
--   меняют inventory и has_stock не пересчитывался — витрина врала о наличии.
--   Добавлен триггер на inventory.
-- R-49: построчный триггер пересчитывал весь товар на каждый вариант (массовое
--   изменение цен по 5000 вариантов → тысячи полных пересчётов). Переведено на
--   statement-level с transition table: один пересчёт на затронутый товар.
-- ─────────────────────────────────────────────────────────────────────────────

drop trigger if exists trg_product_variants_denorm on product_variants;
drop function if exists trg_variant_refresh_denorm();

-- Пересчёт для набора товаров из transition table product_variants.
create or replace function trg_variants_denorm_stmt() returns trigger as $$
begin
    if tg_op = 'DELETE' then
        perform refresh_product_denorm(pid) from (select distinct product_id pid from old_rows) s;
    else
        perform refresh_product_denorm(pid) from (select distinct product_id pid from new_rows) s;
        if tg_op = 'UPDATE' then
            perform refresh_product_denorm(pid) from (select distinct product_id pid from old_rows) s;
        end if;
    end if;
    return null;
end;
$$ language plpgsql;

create trigger trg_product_variants_denorm_ins after insert on product_variants
    referencing new table as new_rows for each statement execute function trg_variants_denorm_stmt();
create trigger trg_product_variants_denorm_upd after update on product_variants
    referencing new table as new_rows old table as old_rows for each statement execute function trg_variants_denorm_stmt();
create trigger trg_product_variants_denorm_del after delete on product_variants
    referencing old table as old_rows for each statement execute function trg_variants_denorm_stmt();

-- Пересчёт при изменении остатков: товары вариантов из transition table inventory.
create or replace function trg_inventory_denorm_stmt() returns trigger as $$
begin
    if tg_op = 'DELETE' then
        perform refresh_product_denorm(pid) from (
            select distinct pv.product_id pid from product_variants pv
            where pv.id in (select variant_id from old_rows)) s;
    else
        perform refresh_product_denorm(pid) from (
            select distinct pv.product_id pid from product_variants pv
            where pv.id in (select variant_id from new_rows)) s;
    end if;
    return null;
end;
$$ language plpgsql;

create trigger trg_inventory_denorm_ins after insert on inventory
    referencing new table as new_rows for each statement execute function trg_inventory_denorm_stmt();
create trigger trg_inventory_denorm_upd after update on inventory
    referencing new table as new_rows for each statement execute function trg_inventory_denorm_stmt();
create trigger trg_inventory_denorm_del after delete on inventory
    referencing old table as old_rows for each statement execute function trg_inventory_denorm_stmt();

-- Текущие значения has_stock недостоверны (менялись остатки без пересчёта) — пересчитать всё.
do $$
declare r record;
begin
    for r in select id from products loop
        perform refresh_product_denorm(r.id);
    end loop;
end $$;
