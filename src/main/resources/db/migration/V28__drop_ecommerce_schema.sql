-- ─────────────────────────────────────────────────────────────────────────────
-- T-16.1: снос схемы e-commerce (онлайн-покупка, доставка, купоны, отзывы).
-- Клиент убрал эти функции; данных в таблицах нет. Покупка уходит в офлайн —
-- «заявка на товар» (T-15.1) в отдельном модуле. Применённые миграции V1-V27 —
-- история, не редактируются; снос делается этой новой миграцией.
-- ─────────────────────────────────────────────────────────────────────────────

-- Гард: убедиться, что ни одна СОХРАНЯЕМАЯ таблица не ссылается на сносимые
-- (иначе drop оставит битые FK или потребует cascade, что скрыло бы проблему).
do $$
declare
    dropped text[] := array['orders','order_items','order_status_history','payments','payment_refunds',
                            'shipments','coupons','coupon_usages','reviews','review_media'];
    offenders text;
begin
    select string_agg(c.conrelid::regclass::text || '.' || c.conname, ', ')
      into offenders
    from pg_constraint c
    where c.contype = 'f'
      and c.confrelid::regclass::text = any (dropped)
      and c.conrelid::regclass::text <> all (dropped);
    if offenders is not null then
        raise exception 'Retained tables still reference e-commerce tables: %', offenders;
    end if;
end $$;

-- Дропы в порядке, обратном зависимостям.
drop table if exists coupon_usages;
drop table if exists coupons;
drop table if exists review_media;
drop table if exists reviews;
drop table if exists payment_refunds;
drop table if exists payments;
drop table if exists shipments;
drop table if exists order_status_history;
drop table if exists order_items;
drop table if exists orders;
-- Индексы поиска заказов (V21) уходят вместе с orders автоматически.

-- Денормализованные поля рейтинга существовали только ради отзывов.
alter table products drop constraint if exists ck_products_rating_avg;
alter table products drop constraint if exists ck_products_rating_count;
alter table products drop column if exists rating_avg;
alter table products drop column if exists rating_count;
