# Машина состояний заявки на товар (T-15.2)

Статусы: `NEW`, `CONTACTED`, `RESERVED`, `COMPLETED`, `CANCELLED`, `LOST`.
Терминальные (`COMPLETED`, `CANCELLED`, `LOST`) исходящих переходов не имеют — запрещённый переход
даёт `409 request.transition_forbidden`.

## Таблица переходов

| Из \ В        | CONTACTED | RESERVED | COMPLETED | CANCELLED | LOST |
|---------------|:---------:|:--------:|:---------:|:---------:|:----:|
| **NEW**       | ✓         |          |           | ✓         | ✓    |
| **CONTACTED** |           | ✓        |           | ✓         | ✓    |
| **RESERVED**  |           |          | ✓         | ✓         | ✓    |
| **COMPLETED** |           |          |           |           |      |
| **CANCELLED** |           |          |           |           |      |
| **LOST**      |           |          |           |           |      |

## Побочные эффекты по резерву

Единственный побочный эффект — резерв остатков через `InventoryService`:

- **→ RESERVED** — резервирует позиции заявки (`reserve`). Нехватка остатка → `409
  inventory.insufficient_stock`, переход не совершается.
- **→ COMPLETED** (только из `RESERVED`) — списывает резерв как продажу (`consume`): физический
  остаток и резерв уменьшаются, движение `SALE`.
- **→ CANCELLED / LOST** — снимает активные резервы (`release`), остаток возвращается в доступный.
  Из статусов без резерва (`NEW`, `CONTACTED`) — no-op.

Резерв держится 3 суток; по истечении фоновый уборщик (`ReservationExpiryService`) снимает резерв и
переводит заявку в `LOST` с причиной `reservation_expired` (см. T-5.2).

## Уведомления

- **→ CONTACTED** и **→ RESERVED** — клиенту уходит уведомление (email/SMS) по шаблону локали
  (`notification.product_request.*`).

## Прочее

- Назначение менеджера — `PATCH /assignee`. Внутренние комментарии — `POST /comments` (лента видна
  только персоналу). Каждый переход пишется в `product_request_history` (from, to, changed_by,
  comment).
