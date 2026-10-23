# Go-live чек-лист (пошаговый план запуска)

Срез: ветка `feature/frontend-storefront`. Код-блокеры из аудитов закрыты; осталось окружение,
секреты, реальный деплой и предстартовые прогоны. Отмечай `[x]` по мере выполнения.

Связанные документы: [deployment.md](deployment.md), [disaster-recovery.md](disaster-recovery.md),
[alerts.md](alerts.md), [performance.md](performance.md), [privacy.md](privacy.md),
[pre-prod-audit-2026-10-22.md](pre-prod-audit-2026-10-22.md),
[production-readiness-2026-10-22.md](production-readiness-2026-10-22.md).

---

## Фаза 0 — код в main

- [ ] Настроить git-remote (сейчас его нет): `git remote add origin <URL>`.
- [ ] Запушить ветку: `git push -u origin feature/frontend-storefront`.
- [ ] Открыть PR → дождаться зелёного `pr.yml` (gradle check + Trivy fs) и фронтовых CI
      (`web-ci`, `admin-ci`, `e2e`).
- [ ] Смёржить в `main` (его сейчас нет — создать как основную ветку; в репо есть `master`).

## Фаза 1 — провижининг инфраструктуры

- [ ] **PostgreSQL 16** (управляемый), приватная сеть.
- [ ] **Redis 7** с паролем, в приватной сети (там лежат refresh-сессии, OTP, лимиты, блокировки).
- [ ] **S3**: публичный бакет (медиа каталога/портфолио) и ПРИВАТНЫЙ бакет (фото заявок — ПДн,
      только presigned GET). Политики доступа, CORS на бакете под домен витрины.
- [ ] **SMTP-провайдер** (прод-почта) и **SMS-шлюз** (OTP). Подтверждённый домен отправителя
      (SPF/DKIM/DMARC) — иначе письма в спам.
- [ ] **Container registry** (GHCR уже в `main.yml`).
- [ ] **Компьютинг**: ECS/k8s/др. для 3 сервисов (backend, web, admin-nginx).
- [ ] **CDN/reverse-proxy + TLS**: HTTPS, домены витрины/админки/API; прокси `/api` админки на backend
      (тогда CSP `connect-src 'self'` работает), TLS-терминация, проставление `X-Forwarded-*`.

## Фаза 2 — секреты и переменные окружения

В Secrets Manager / SSM, НЕ в репозитории. Приложение в prod не стартует без них (fail-closed:
`ProdSecretsGuard` отвергает dev-дефолты, пустые и короткие <24 симв.).

**Обязательные секреты бэкенда (без дефолтов):**
- [ ] `JWT_SECRET` (длинный случайный, ≥32 байт)
- [ ] `NOTIFICATION_WEBHOOK_SECRET`
- [ ] `NOTIFICATION_UNSUBSCRIBE_SECRET`
- [ ] `SPAM_SIGNING_SECRET`
- [ ] `PRIVACY_PSEUDONYM_SECRET`

**Подключения/конфиг бэкенда:**
- [ ] `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`
- [ ] `REDIS_HOST/REDIS_PORT/REDIS_PASSWORD`
- [ ] `MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD/MAIL_FROM`, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true`
- [ ] `S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET/S3_BUCKET_PRIVATE/S3_REGION`,
      `S3_PATH_STYLE=false` (для реального AWS), `S3_PUBLIC_BASE_URL` (CDN/домен медиа)
- [ ] `CORS_ALLOWED_ORIGINS` (домены витрины и админки)
- [ ] `TRUSTED_PROXIES` (CIDR ALB/прокси — иначе X-Forwarded-For игнорируется)
- [ ] `APP_BASE_URL` (API), `APP_ADMIN_URL` (админка), `APP_STOREFRONT_URL` (витрина)
- [ ] `SPRING_FLYWAY_ENABLED=false` (миграции — отдельным шагом CI), `SPRING_PROFILES_ACTIVE=prod`
      (уже в Dockerfile; не переопределять)
- [ ] `BOOTSTRAP_ADMIN_EMAIL` + `BOOTSTRAP_ADMIN_PASSWORD` — первый ADMIN создаётся один раз на пустой БД

**Build-args фронтендов (инлайнятся на сборке образа — задать в CI vars):**
- [ ] `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_SITE_URL` (витрина)
- [ ] `VITE_API_BASE_URL` (админка; пусто, если `/api` на том же origin через прокси)
- [ ] (опц.) `NEXT_PUBLIC_PLAUSIBLE_SRC`, `NEXT_PUBLIC_MEDIA_BASE_URL` (нужны для CSP витрины)

## Фаза 3 — довести деплой-конвейер (сейчас шаги `deploy-*` — шаблон)

В [.github/workflows/main.yml](../.github/workflows/main.yml):
- [ ] `Run Flyway migrations` — реальный `flyway migrate` с prod URL/кредами из секретов, ДО старта
      новых контейнеров (миграции обратно совместимы — старый код живёт с новой схемой).
- [ ] `Deploy` — реальная выкатка 3 образов (`:sha-<commit>`) в кластер (ECS/k8s manifests/Helm).
- [ ] `Smoke` — `curl -f https://<api>/actuator/health/readiness` + базовый сценарий.
- [ ] Прод-джоба с ручным подтверждением (environment protection) — уже заведена; настроить
      reviewers. Предыдущий тег хранить для отката.

## Фаза 4 — доводка безопасности окружения

- [ ] **CSP витрины**: сейчас `Content-Security-Policy-Report-Only` (из env). Собрать отчёты о
      нарушениях на staging, выставить `NEXT_PUBLIC_MEDIA_BASE_URL`/источники, затем переключить на
      enforcing (заголовок без `-Report-Only` в `web/next.config.ts`).
- [ ] **ESP + List-Unsubscribe**: подключить реального почтового провайдера; добавить заголовки
      `List-Unsubscribe`/`List-Unsubscribe-Post` (Gmail/Yahoo требуют one-click для массовых рассылок,
      в Израиле простая отписка обязательна) — это остаток п.7, завязан на провайдера.
- [ ] **WhatsApp (опционально)**: канал за флагом `NOTIFICATION_WHATSAPP_ENABLED`. Для включения —
      Meta WhatsApp Business: `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, завести
      ОДОБРЕННЫЕ шаблоны и прописать их имена в `app.notification.whatsapp.templates.*`, перевести
      нужные уведомления на канал `WHATSAPP` (см. notifications.md). По умолчанию выключен.
- [ ] Prometheus/метрики: в prod наружу закрыто (`health,info`). Если нужен скрейп — отдельный
      защищённый порт/сеть, не публично.
- [ ] Dependabot/Renovate — регулярный скан зависимостей (разовый уже чист).

## Фаза 5 — предстартовые прогоны (staging)

- [ ] `e2e` (папка `e2e/`), Lighthouse CI, axe — зелёные против staging.
- [ ] Нагрузочный прогон `perf/k6` ([performance.md](performance.md)); проверить алерты
      ([alerts.md](alerts.md)).
- [ ] **Учебное восстановление** из бэкапа (`ops/backup`, [disaster-recovery.md](disaster-recovery.md)).
- [ ] Проверить, что приложение НЕ стартует с dev-секретом (ProdSecretsGuard) и что при незаданном
      профиле цепочка Security закрыта (fail-closed).
- [ ] Проверить сквозные флоу писем на staging: приглашение сотрудника → `/set-password`, сброс
      пароля → `/reset-password`, подтверждение email → `/verify-email`, отписка → `/unsubscribe`
      (ссылки ведут на витрину/админку, не на API).
- [ ] Бэкап БД включён, расписание и ретеншен заданы.

## Фаза 6 — продакшен-cutover

- [ ] DNS на домены (витрина/админка/API), TLS-сертификаты валидны.
- [ ] Flyway migrate (prod) → деплой образов → smoke readiness.
- [ ] Создать первого ADMIN (bootstrap env на пустой БД) → войти в админку → завести реальных
      сотрудников через «Сотрудники» (инвайт-ссылки).
- [ ] Снять/сменить флаги по необходимости (регистрация CUSTOMER по умолчанию закрыта — так и
      оставить, если не нужна).

## Фаза 7 — после запуска

- [ ] **Ротировать/удалить `dev-accounts.env.local`** (п.21) и все dev-дефолтные секреты.
- [ ] Мониторинг/алерты активны; дэшборды метрик уведомлений, склад, воронка.
- [ ] Переключить CSP витрины в enforcing (после чистых отчётов).
- [ ] Зафиксировать теги образов и процедуру отката.

---

**Статус готовности кода:** бэкенд — 520 тестов зелёные (Spring Boot 3.5.16) + порог покрытия;
витрина/админка — build/typecheck/тесты зелёные, `pnpm audit --prod` чист. Остаток — инфраструктура и
окружение из фаз выше.
