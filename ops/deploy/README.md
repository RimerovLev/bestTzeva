# Деплой на VM (docker-compose)

Прод-стек на одной VM: бэкенд + витрина + админка (образы из GHCR) + Postgres/Redis/MinIO (тома) +
Caddy (авто-TLS и маршрутизация). Файлы: `docker-compose.prod.yml`, `Caddyfile`, `.env.prod.example`.

## 0. Предпосылки
- VM (Ubuntu/Debian), Docker + Compose plugin.
- Публичные DNS A/AAAA на IP VM для 4 доменов: `API_DOMAIN`, `WEB_DOMAIN`, `ADMIN_DOMAIN`,
  `MEDIA_DOMAIN`. Порты 80/443 открыты (Caddy выпускает Let's Encrypt).
- Доступ к GHCR (образы приватные → `docker login ghcr.io`).

## 1. Первый запуск
```bash
# на VM, в каталоге с этими файлами:
cp .env.prod.example .env && $EDITOR .env        # заполнить секреты/домены
docker login ghcr.io                              # если образы приватные
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
docker compose -f docker-compose.prod.yml --env-file .env ps
curl -fsS https://$API_DOMAIN/actuator/health/readiness
```
Первый `up` создаёт бакеты (minio-init), применяет миграции (Flyway при старте app, т.к.
`SPRING_FLYWAY_ENABLED=true` — один инстанс, гонки нет) и создаёт первого ADMIN из
`BOOTSTRAP_ADMIN_*`. Войти в админку: `https://$ADMIN_DOMAIN`.

## 2. Обновление (новая версия)
```bash
export IMAGE_TAG=sha-<commit>     # или latest
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```
Предыдущие образы остаются в кэше — откат = прежний `IMAGE_TAG` + `up -d`.

## 3. Медиа и presigned-ссылки (важно)
Presigned-ссылки (приватные фото заявок, выдача персоналу) подписываются под `S3_ENDPOINT`, поэтому
он **должен быть доступен браузеру**. Варианты:
- **Managed S3/CDN (рекомендуется для нагрузки):** указать `S3_ENDPOINT`/`S3_PUBLIC_BASE_URL` на него,
  `S3_PATH_STYLE=false`; MinIO из compose тогда можно убрать.
- **MinIO на VM:** `S3_ENDPOINT=S3_PUBLIC_BASE_URL=https://$MEDIA_DOMAIN`; Caddy проксирует домен на
  MinIO с сохранением Host (SigV4 сойдётся). Публичный бакет читается анонимно (создаётся minio-init).

## 4. CSP витрины
`web` отдаёт CSP в **Report-Only**. После проверки отчётов (источники медиа/Plausible) переключить на
enforcing: убрать `-Report-Only` в `web/next.config.ts`, пересобрать образ. Админка — enforcing CSP
сразу (её origin'ы известны; `/api` проксируется тем же доменом).

## 5. Бэкапы и восстановление
См. [../../docs/disaster-recovery.md](../../docs/disaster-recovery.md) и `../backup`. Том `pgdata`
бэкапить регулярно; приватный бакет (`miniodata`) — тоже (ПДн-фото).

## 6. Автодеплой из CI
`main.yml` (job `deploy-prod`) по SSH подключается к VM, делает `pull` + `up -d` и smoke по
readiness. Нужны GitHub-секреты окружения `production`: `DEPLOY_SSH_HOST`, `DEPLOY_SSH_USER`,
`DEPLOY_SSH_KEY`, путь `DEPLOY_PATH` (каталог с compose+.env на VM). `.env` живёт на VM, в CI не
передаётся.
