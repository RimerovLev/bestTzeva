# Машина состояний заявки на покраску (T-11.3)

Статусы: `NEW`, `CONTACTED`, `MEASUREMENT_SCHEDULED`, `MEASURED`, `QUOTED`, `ACCEPTED`,
`IN_PROGRESS`, `COMPLETED`, плюс ветки `REJECTED` и `LOST`.

Терминальные (`COMPLETED`, `REJECTED`, `LOST`) исходящих переходов не имеют — запрещённый переход
даёт `409 service_request.transition_forbidden`.

## Таблица переходов

| Из \ В                     | CONTACTED | MEAS_SCHED | MEASURED | QUOTED | ACCEPTED | IN_PROGRESS | COMPLETED | REJECTED | LOST |
|----------------------------|:---------:|:----------:|:--------:|:------:|:--------:|:-----------:|:---------:|:--------:|:----:|
| **NEW**                    | ✓         |            |          |        |          |             |           | ✓        | ✓    |
| **CONTACTED**              |           | ✓          |          |        |          |             |           | ✓        | ✓    |
| **MEASUREMENT_SCHEDULED**  |           |            | ✓        |        |          |             |           | ✓        | ✓    |
| **MEASURED**               |           |            |          | ✓      |          |             |           | ✓        | ✓    |
| **QUOTED**                 |           |            |          |        | ✓        |             |           | ✓        | ✓    |
| **ACCEPTED**               |           |            |          |        |          | ✓           |           | ✓        | ✓    |
| **IN_PROGRESS**            |           |            |          |        |          |             | ✓         | ✓        | ✓    |
| **COMPLETED/REJECTED/LOST**|           |            |          |        |          |             |           |          |      |

Каждый переход пишется в `service_request_history` (from, to, changed_by, comment).

## Обогащённые переходы

Общий эндпоинт `POST /{id}/transitions` выполняет любой разрешённый переход. Два перехода имеют
специальные эндпоинты с побочными эффектами:

- **→ MEASUREMENT_SCHEDULED** — `POST /{id}/measurement` c датой/временем: сохраняет `measurement_at`
  и ставит **отложенное** напоминание клиенту за сутки (`MEASUREMENT_REMINDER`) через очередь
  уведомлений (T-12.2), а не собственный планировщик.
- **→ QUOTED** — `POST /{id}/quote` c ценой/составом/сроком: генерирует PDF коммерческого предложения
  на иврите (встроенный шрифт Noto Sans Hebrew, RTL), кладёт его в приватный бакет
  (`quote_pdf_key`), уведомляет клиента (`SERVICE_REQUEST_QUOTED`). PDF выдаётся персоналу presigned
  GET через `GET /{id}/quote-pdf`.

## Назначение менеджера

- `PATCH /{id}/assignee` — ручное назначение; назначенному уходит уведомление.
- `POST /{id}/auto-assign` — автораспределение по кругу (наименее загруженный активными заявками
  менеджер). За флагом `app.service.auto-assign-enabled`; выключено → `409`.

## Прочее

Внутренние комментарии — `POST /{id}/comments` (лента `service_request_comments`, видна только
персоналу). Список — `GET /admin/service-requests` с фильтрами (статус, тип объекта, город, менеджер,
период, диапазон площади, источник) и поиском по номеру/телефону/имени; агрегаты — `GET /metrics`.
Все изменяющие операции пишутся в `audit_log`.
