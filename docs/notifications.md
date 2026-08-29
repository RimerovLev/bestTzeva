# Уведомления (T-12.1)

Модуль `com.tzevabest.notification` рассылает уведомления клиентам и персоналу. T-12.1 — ядро и
шаблоны; асинхронную отправку с ретраями добавляет T-12.2, реальные каналы — T-12.3.

## Устройство

- **Каналы** — интерфейсы `EmailSender`, `SmsSender`, `WhatsappSender`. Реализации до T-12.3 —
  логирующие заглушки (`Logging*Sender`).
- **Фасад** — `NotificationService.send(template, recipient, locale, model)`: рендерит тему/тело,
  выбирает канал по шаблону, отправляет, пишет снимок в `notifications_log` (канал, код, получатель,
  локаль, тема, тело, статус, номер попытки). `render(...)` — та же логика без отправки (для тестов и
  предпросмотра).
- **Каталог шаблонов** — enum `NotificationTemplate`: код, канал, базовый ключ сообщений, флаг
  «внутреннее». 14 кодов: `product_request_received/contacted/reserved/expired`,
  `service_request_received/quoted`, `measurement_reminder`, `password_reset`, `email_verification`,
  `otp_code`, `account_locked`, `token_replay`, `low_stock_alert` и `new_request_admin` (два
  последних — внутренние, персоналу).
- **Модели** — строго типизированные record'ы (`ProductRequestModel`, `LinkModel`, `OtpModel`, …),
  реализующие `NotificationModel`. Поля в порядке объявления дают позиционные аргументы `{0}, {1}, …`
  для `MessageFormat`. Отсутствующее поле ловится на компиляции у вызывающего.

## Локализация и текст

Текст живёт в бандлах `messages*.properties` — **это и есть «отдельные файлы на локаль»**. На каждый
шаблон ключи `<keyStem>.subject` (для email) и `<keyStem>.body`. Часть кодов переиспользует уже
существующие ключи (`notification.product_request.created/contacted/reserved`, `notification.token_replay`),
чтобы не дублировать текст. Локаль: `he` / `ru` / `en`, дефолт `he`; берётся из
`product_requests.locale` (V37), профиля пользователя или заявки на покраску.

## Вёрстка email (RTL)

Один общий Thymeleaf-layout `templates/notifications/email.html`: табличная вёрстка ради
совместимости с почтовыми клиентами, инлайн-стили, шапка/подвал компании. Направление и выравнивание
берутся из локали (`dir=rtl` и выравнивание по правому краю для иврита). SMS/WhatsApp — простой текст
без HTML.

> **Отклонение от буквальной формулировки задачи.** Вместо 14×3 отдельных HTML-файлов сделан один
> параметризованный RTL-layout, а текст вынесен в бандлы по локалям. Это DRY-эквивалент требования
> («Thymeleaf + отдельные файлы на локаль + общий базовый layout») без 42 почти одинаковых копий.
> Пер-шаблонные HTML-фрагменты можно добавить точечно, если понадобится нетривиальная вёрстка
> конкретного письма.

## Что уже в бандлах (пункт 7)

Тексты `account_locked` (в iam — ключи `notification.suspicious_login.*`) и `token_replay`
(`notification.token_replay.*`) уже лежали в бандлах, не в коде (поправлено ранее по R-13). Коды
шаблонов `ACCOUNT_LOCKED`/`TOKEN_REPLAY` заведены в каталоге; перевод вызовов iam на фасад
`NotificationService` — при интеграции соответствующих потоков.

## Журнал

Каждая отправка — строка в `notifications_log` со снимком тела, статусом (`SENT`/`FAILED`) и номером
попытки. Ошибка канала не пробрасывается наверх (фиксируется как `FAILED`); устойчивость и ретраи —
T-12.2.

## Асинхронная отправка (T-12.2)

Transactional outbox поверх `notifications_log` (V39: `next_attempt_at`, `idempotency_key`,
`priority`).

- **Постановка в очередь** — `NotificationService.enqueue(...)` пишет строку `QUEUED` в ТЕКУЩЕЙ
  транзакции (вместе с бизнес-операцией), поэтому уведомление переживает рестарт между коммитом и
  отправкой. `notify(...)` маршрутизирует: `IMMEDIATE` (OTP, сброс пароля) уходит сразу через
  `send(...)`, остальное — в очередь.
- **Обработчик** — `NotificationDispatcher.dispatchDue()`: под распределённой блокировкой в Redis
  (`SET NX` + TTL, как уборка резервов по R-42) забирает готовые (`status='QUEUED' and
  next_attempt_at <= now()`) пачками по 100 с `FOR UPDATE SKIP LOCKED` — два инстанса берут
  непересекающиеся строки и не шлют дубли. Планировщик `NotificationDispatchScheduler`
  (`@Profile("!test")`) вызывает его по `app.notification.dispatch-interval`.
- **Ретраи** — экспоненциальная задержка 1/5/15/60 мин, максимум 5 попыток, затем `FAILED` и
  `log.error`. `NonRetryableNotificationException` (неверный адрес) не ретраится — сразу `FAILED`.
- **Дедупликация** — уникальный ключ `templateCode:recipient:referenceId`; `enqueue` использует
  `on conflict (idempotency_key) do nothing`, чтобы дубль не абортил бизнес-транзакцию. Ключ `null`
  (без referenceId) не участвует в дедупе (nulls различны в уникальном индексе).
- **Метрики** (`NotificationMetrics`, actuator): `notifications.queue.size` (gauge),
  `notifications.sent`/`failed`/`retried` по каналам (counters), `notifications.delivery` (timer).

## Тесты

`NotificationRenderTest`: каждый шаблон рендерится на `he`/`ru`/`en` без незаполненных плейсхолдеров,
email на иврите содержит `dir="rtl"`; `send` пишет строку в `notifications_log` со снимком.

`NotificationOutboxTest`: уведомление переживает «рестарт» между коммитом и отправкой; два
параллельных обработчика не шлют дубли (SKIP LOCKED); ретраи с бэкоффом и финальный `FAILED` после
максимума; `NonRetryable` → сразу `FAILED`; `IMMEDIATE` мимо очереди; дедуп по ключу идемпотентности.
