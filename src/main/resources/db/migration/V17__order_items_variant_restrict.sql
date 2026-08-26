-- ─────────────────────────────────────────────────────────────────────────────
-- R-16: order_items.variant_id ссылался на product_variants с on delete set null.
-- Правило T-4.2: жёсткое удаление варианта возможно только если он никогда не
-- участвовал в заказах. Со SET NULL удаление проходило и молча обнуляло ссылку,
-- теряя связь позиции заказа с каталогом (отчёт «продажи по вариантам» начинал
-- недосчитывать). Переводим на RESTRICT: удаление варианта с историей заказов
-- отклоняется базой.
-- ─────────────────────────────────────────────────────────────────────────────

alter table order_items drop constraint fk_order_items_variant_id;

alter table order_items add constraint fk_order_items_variant_id
    foreign key (variant_id) references product_variants (id) on delete restrict;
