-- Редактируемые овнером картинки сайта по слотам (hero_can, works, banner, logo). Файл лежит в
-- публичном бакете; здесь храним ключ объекта и тип, публичный URL вычисляется при чтении.
create table site_images (
    slot          varchar(40)  primary key,
    object_key    varchar(400) not null,
    content_type  varchar(100) not null,
    updated_at    timestamptz  not null default now(),
    updated_by    bigint
);
