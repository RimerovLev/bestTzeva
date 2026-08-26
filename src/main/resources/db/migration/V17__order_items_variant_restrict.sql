-- ─────────────────────────────────────────────────────────────────────────────
-- R-16: order_items.variant_id переводится с on delete set null на restrict.
--
-- Правило T-4.2: физически удалить вариант можно, только если он никогда не
-- участвовал в заказах. Со SET NULL удаление проходило и тихо обнуляло ссылку в
-- позициях заказа — снимки полей сохраняли отображение истории, но связь с
-- каталогом терялась, и отчёт «продажи по вариантам» начинал недосчитывать.
-- RESTRICT запрещает такое удаление на уровне БД (для снятия варианта с продажи
-- есть is_active).
-- ─────────────────────────────────────────────────────────────────────────────

alter table order_items drop constraint fk_order_items_variant_id;

alter table order_items add constraint fk_order_items_variant_id
    foreign key (variant_id) references product_variants (id) on delete restrict;
