# Правила алертов (T-14.1)

Метрики отдаются в `/actuator/prometheus`. Пороги — ориентировочные, уточняются на T-14.5.

| Алерт | Условие | Порог | Действие |
|---|---|---|---|
| Высокая доля 5xx | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m])` | > 1% за 5 мин | страница дежурному |
| Медленный листинг каталога | `histogram_quantile(0.95, http_server_requests_seconds_bucket{uri="/api/v1/products"})` | p95 > 300 мс | расследование |
| Медленная отправка заявки | p95 `POST /api/v1/product-requests` \| `/service-requests` | > 800 мс | расследование |
| Очередь уведомлений растёт | `notifications_queue_size` | > 500 за 10 мин | проверить обработчик/провайдеров |
| Высокая доля неудачных SMS | `rate(notifications_failed_total{channel="SMS"}[15m])` | > 5% | проверить SMS-провайдера |
| Заявки без менеджера > суток | бизнес-метрика (заявки в NEW/CONTACTED без assigned_manager_id) | есть старше 24ч | уведомить менеджеров |
| Просроченные резервы не убраны | `stock_reservations` ACTIVE с `expires_at < now()` | > 0 за 15 мин | проверить ReservationExpiryScheduler |
| БД/Redis/S3 недоступны | `/actuator/health` компонент DOWN | любой | страница дежурному |
| Пул соединений исчерпан | `hikaricp_connections_active / hikaricp_connections_max` | > 0.9 за 5 мин | масштабировать/расследовать |

## Наблюдаемость

- **Логи**: JSON в prod (logstash-encoder), человекочитаемые в local. Поля: `timestamp`, `level`,
  `logger`, `message`, `trace_id`, `user_id`, `request_uri`, `duration_ms`. Сквозной `trace_id` из
  `X-Request-Id` (RequestIdFilter), MDC очищается в finally.
- **Маскирование**: пароли/токены/секреты, номера карт, телефоны, email маскируются logback-
  конвертером `%mask` (LogMasker) — не полагаемся на дисциплину.
- **Метрики**: время ответа по эндпоинтам (`http_server_requests`), бизнес-счётчики уведомлений
  (`notifications_*`), пул БД (`hikaricp_*`), JVM. Prometheus-формат — `/actuator/prometheus`.
- **Health**: БД и Redis — авто; S3 — `S3HealthIndicator`. Health открыт, остальное закрыто в
  prod-цепочке Security.
- **Sentry/аналог**: интеграция с прокидыванием `trace_id` и user-context, фильтрацией ожидаемых
  бизнес-исключений — точка расширения (подключается конфигурацией DSN из окружения); ожидаемые
  4xx/валидация в алерты не идут.
