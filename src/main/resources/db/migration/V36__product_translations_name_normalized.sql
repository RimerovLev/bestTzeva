-- ─────────────────────────────────────────────────────────────────────────────
-- R-48: поиск шёл гарантированным seq scan — триграммный GIN-индекс был на сырой
-- колонке name, а условие применяло к ней unaccent(regexp_replace(...)), то есть
-- индекс по колонке под индекс по выражению не подходил.
-- Нормализация выносится в хранимую generated-колонку с GIN-триграммным индексом.
-- unaccent сам по себе не immutable — оборачиваем в immutable-функцию (известный
-- приём, обязателен для generated-колонки).
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function immutable_unaccent(text) returns text
    language sql immutable strict parallel safe
    as $$ select unaccent('unaccent', $1) $$;

-- name_normalized = снятие никуд (огласовки U+0591..U+05C7) + unaccent.
alter table product_translations
    add column name_normalized text
    generated always as (
        immutable_unaccent(regexp_replace(name, '[' || chr(1425) || '-' || chr(1479) || ']', '', 'g'))
    ) stored;

create index ix_product_translations_name_norm_trgm
    on product_translations using gin (name_normalized gin_trgm_ops);

-- Старый индекс по сырому name больше не используется — поиск ушёл на name_normalized.
drop index if exists ix_product_translations_name_trgm;
