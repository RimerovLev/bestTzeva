-- ─────────────────────────────────────────────────────────────────────────────
-- Составные индексы для списков каталога.
--
-- Форма выбрана по правилам, а не только по замеру на маленьком датасете:
--   * is_active НЕ входит в ключ (низкоселективен) — вынесен в частичный предикат
--     WHERE is_active, за счёт чего индекс меньше, а created_at идёт сразу за
--     equality-колонками;
--   * created_at desc в хвосте окупается на Index Scan; на IN-фильтре по paint_type
--     (T-4.3) полностью бесплатной сортировки не будет — финальная форма и подтверждение
--     бюджета переносятся на T-4.3 (см. docs/db-query-plans.md).
-- ─────────────────────────────────────────────────────────────────────────────

create index ix_products_catalog_list
    on products (category_id, paint_type, created_at desc)
    where is_active;

create index ix_products_featured
    on products (created_at desc)
    where is_active and is_featured;
