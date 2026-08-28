# Хранилище медиа (T-4.7)

Медиа каталога (изображения/видео товаров и вариантов) хранятся в S3-совместимом бакете: **MinIO**
в dev/CI, реальный **S3** в проде. Модуль `catalog` не знает про S3 SDK — он работает через
абстракцию `media.service.MediaStorage`.

## Модули

- **`media`** — инфраструктура хранения, без доменных зависимостей:
  - `MediaStorage` / `StoredObject` — абстракция (`put` / `delete` / `publicUrl`);
  - `S3MediaStorage` — реализация поверх AWS SDK v2 (`software.amazon.awssdk:s3`);
  - `StorageProperties` (`app.storage.s3.*`), `MediaStorageConfig` — S3-клиент.
- **`catalog`** — привязка к товару:
  - `ProductMediaService` — загрузка/удаление, ведёт строку `product_media`;
  - `AdminProductMediaController` — админ-API (`MANAGER`/`ADMIN`);
  - витрина отдаёт медиа в `ProductCard.media` и `VariantCard.media`.

## Данные

`product_media` (из V3): владелец — ровно один из `product_id` / `variant_id` (XOR), `url` —
публичная ссылка, `type`, `sort_order`, `is_primary`, `alt_translations` (jsonb).

- **V38** добавляет `object_key` — устойчивый ключ объекта в бакете (для удаления; `url` может
  смениться при переезде CDN).
- **Единственность главного** (V24): частичные уникальные индексы по `(product_id) where is_primary`
  и `(variant_id) where is_primary`. `ProductMediaService` снимает прежний primary в той же
  транзакции перед вставкой нового — индекс остаётся страховкой.

## API

- `POST /api/v1/admin/products/{id}/media` — multipart `file` + `type` (default IMAGE),
  `sort_order`, `primary`.
- `POST /api/v1/admin/variants/{id}/media` — то же для варианта.
- `DELETE /api/v1/admin/media/{id}` — сносит объект и строку.

Валидация загрузки: непустой файл, размер ≤ 10 МБ, MIME-тип соответствует заявленному `type`.

## Конфигурация (`app.storage.s3`)

| ключ | env | назначение |
|---|---|---|
| `endpoint` | `S3_ENDPOINT` | адрес S3; для MinIO `http://host:9000`, для реального S3 можно пусто |
| `access-key` / `secret-key` | `S3_ACCESS_KEY` / `S3_SECRET_KEY` | учётные данные |
| `bucket` | `S3_BUCKET` | публичный бакет медиа каталога |
| `private-bucket` | `S3_BUCKET_PRIVATE` | приватный бакет (фото заявок — ПДн, только presigned GET) |
| `region` | `S3_REGION` | регион |
| `path-style-access` | `S3_PATH_STYLE` | обязателен для MinIO |
| `public-base-url` | `S3_PUBLIC_BASE_URL` | база публичных ссылок; пусто → `endpoint` (в проде — CDN/домен) |

## Тесты

`ProductMediaTest` поднимает MinIO через Testcontainers (`MinIOContainer`, синглтон в
`IntegrationTest`, endpoint пробрасывается `@DynamicPropertySource`): загрузка кладёт объект в бакет
и строку, витрина отдаёт ссылку, второй `primary` снимает первый, удаление сносит объект и строку,
неверный MIME → 422.
