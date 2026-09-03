-- ─────────────────────────────────────────────────────────────────────────────
-- R-67: осиротевшие загрузки фото. Публичный presign выдаёт ссылку на PUT в
-- приватный бакет, но объект, загруженный по ней и не привязанный к заявке, не
-- существует ни в одной таблице — его не удалит ни ретеншен (он по
-- service_request_media), ни анонимизация. Это фото чужих квартир, лежащие вечно.
--
-- Фиксируем факт выдачи presigned URL: ключ, ip, время. Привязка к заявке помечает
-- запись использованной (used_at). Фоновая уборка удаляет из S3 и из таблицы всё
-- неиспользованное старше суток. Та же таблица служит для проверки при привязке,
-- что ключ действительно выдавался нами, а не подобран.
-- ─────────────────────────────────────────────────────────────────────────────

create table media_upload_intents (
    id         bigint generated always as identity primary key,
    object_key varchar(512) not null,
    ip         varchar(45),
    created_at timestamptz  not null default now(),
    used_at    timestamptz,
    constraint uq_media_upload_intents_key unique (object_key)
);

-- Уборка ходит по неиспользованным старше суток.
create index ix_media_upload_intents_unused on media_upload_intents (created_at) where used_at is null;
