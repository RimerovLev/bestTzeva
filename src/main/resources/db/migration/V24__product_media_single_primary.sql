-- ─────────────────────────────────────────────────────────────────────────────
-- R-30: не более одного главного изображения на товар и на вариант.
-- Флаг is_primary был без ограничения — две главные картинки у одного товара
-- давали недетерминированную миниатюру в листинге (зависела от порядка строк).
-- Частичные уникальные индексы: по одному primary на product_id и на variant_id.
-- ─────────────────────────────────────────────────────────────────────────────

create unique index uq_product_media_primary_product
    on product_media (product_id) where is_primary and product_id is not null;

create unique index uq_product_media_primary_variant
    on product_media (variant_id) where is_primary and variant_id is not null;
