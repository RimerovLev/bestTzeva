-- ─────────────────────────────────────────────────────────────────────────────
-- R-57: разделяем захват и отправку. Раньше пачка из 100 сообщений отправлялась
-- внутри одной транзакции с FOR UPDATE — блокировки строк и соединение из пула
-- держались всё время сетевых вызовов, а откат хоронил отметки SENT по уже
-- отправленным сообщениям (повтор целой пачки).
--
-- Теперь: в короткой транзакции строки помечаются 'SENDING' (с locked_by/locked_at)
-- и коммитятся; отправка и запись результата идут вне транзакции, по одному
-- сообщению. Зависшие 'SENDING' старше таймаута возвращает в 'QUEUED' отдельный
-- проход — иначе падение процесса подвесило бы сообщение навсегда.
-- ─────────────────────────────────────────────────────────────────────────────

alter table notifications_log
    add column locked_by  varchar(64),
    add column locked_at  timestamptz;

-- Разрешаем новый транзитный статус SENDING.
alter table notifications_log drop constraint ck_notifications_log_status;
alter table notifications_log
    add constraint ck_notifications_log_status
        check (status in ('QUEUED', 'SENDING', 'SENT', 'FAILED'));

-- Возврат зависших SENDING идёт по locked_at — отдельный индекс частичный.
create index ix_notifications_log_sending on notifications_log (locked_at)
    where status = 'SENDING';
