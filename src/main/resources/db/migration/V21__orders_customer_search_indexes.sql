-- ─────────────────────────────────────────────────────────────────────────────
-- R-20: индексы под админский поиск заказов (T-7.4).
-- Список заказов в админке ищет по телефону, email и имени клиента — под эти поля
-- индексов не было, и на датасете large (100k заказов) поиск читал таблицу целиком
-- (первое, что делает оператор поддержки при звонке).
--   * customer_phone      — обычный btree (равенство/префикс);
--   * lower(customer_email) — функциональный btree (регистронезависимый поиск);
--   * customer_name        — триграммный GIN (ILIKE '%...%'), как для
--                            product_translations.name.
-- ─────────────────────────────────────────────────────────────────────────────

create index ix_orders_customer_phone       on orders (customer_phone);
create index ix_orders_customer_email_lower on orders (lower(customer_email));
create index ix_orders_customer_name_trgm   on orders using gin (customer_name gin_trgm_ops);
