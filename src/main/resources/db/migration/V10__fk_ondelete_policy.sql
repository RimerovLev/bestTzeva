-- ─────────────────────────────────────────────────────────────────────────────
-- Явная политика on delete: журналы и история — RESTRICT (нельзя удалить сущность,
-- пока по ней есть аудит/история). Переводы и медиа уже CASCADE, ссылки на
-- пользователя в заказах — SET NULL (заказ переживает удаление аккаунта).
-- Здесь доводятся 4 FK журналов/истории, которые оставались CASCADE.
-- ─────────────────────────────────────────────────────────────────────────────

alter table order_status_history
    drop constraint fk_order_status_history_order_id,
    add constraint fk_order_status_history_order_id
        foreign key (order_id) references orders (id) on delete restrict;

alter table service_request_history
    drop constraint fk_service_request_history_request_id,
    add constraint fk_service_request_history_request_id
        foreign key (request_id) references service_requests (id) on delete restrict;

alter table coupon_usages
    drop constraint fk_coupon_usages_coupon_id,
    add constraint fk_coupon_usages_coupon_id
        foreign key (coupon_id) references coupons (id) on delete restrict;

alter table coupon_usages
    drop constraint fk_coupon_usages_order_id,
    add constraint fk_coupon_usages_order_id
        foreign key (order_id) references orders (id) on delete restrict;
