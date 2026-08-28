-- ─────────────────────────────────────────────────────────────────────────────
-- R-41: журнал движений должен сходиться — sum(qty_delta) по варианту/складу равно
--   inventory.qty. Резерв физический остаток не двигает (меняет reserved_qty),
--   поэтому изменение резерва выносится в отдельные колонки reserved_delta/
--   reserved_after, а qty_delta резервных движений = 0. qty_after всегда равен
--   фактическому qty после операции.
-- R-50: проданный резерв был неотличим от отменённого (оба RELEASED). Добавлены
--   статус CONSUMED и колонка released_reason.
-- ─────────────────────────────────────────────────────────────────────────────

alter table inventory_movements
    add column reserved_delta integer not null default 0,
    add column reserved_after integer;

alter table stock_reservations drop constraint ck_stock_reservations_status;
alter table stock_reservations add constraint ck_stock_reservations_status
    check (status in ('ACTIVE', 'RELEASED', 'EXPIRED', 'CONSUMED'));

alter table stock_reservations add column released_reason varchar(24);
alter table stock_reservations add constraint ck_stock_reservations_reason
    check (released_reason is null or released_reason in ('manual', 'expired', 'sold', 'request_cancelled'));
