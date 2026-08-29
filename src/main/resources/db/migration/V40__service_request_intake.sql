-- ─────────────────────────────────────────────────────────────────────────────
-- T-11.1: приём заявки на покраску. Номер из последовательности (как R-46 для
-- заявок на товар). Локаль клиента сохраняется (как R-53). Фото помещения —
-- персональные данные: лежат в приватном бакете, в media храним только ключ
-- объекта (object_key), публичной ссылки нет — выдача через presigned GET.
-- ─────────────────────────────────────────────────────────────────────────────

create sequence service_request_number_seq;

alter table service_requests
    add column locale varchar(2) not null default 'he'
    constraint ck_service_requests_locale check (locale in ('he', 'ru', 'en'));

alter table service_request_media
    add column object_key varchar(512);

-- Приватные фото не имеют публичного url — храним ключ объекта, url необязателен.
alter table service_request_media
    alter column url drop not null;
