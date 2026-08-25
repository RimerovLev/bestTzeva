# Tzeva Best — команды локальной разработки
.DEFAULT_GOAL := help
.PHONY: help env infra-up infra-down infra-logs dev-up down clean seed

# Создать .env из шаблона, если его ещё нет.
env:
	@test -f .env || (cp .env.example .env && echo "Создан .env из .env.example")

# Поднять инфраструктуру (postgres, redis, minio, mailpit) и дождаться healthy.
# --wait ждём только на долгоживущих сервисах: одноразовый minio-init завершается
# (exit 0), и включение его в --wait трактовалось бы как ошибка.
infra-up: env
	docker compose up -d --wait postgres redis minio mailpit
	docker compose up -d minio-init

# Остановить инфраструктуру (данные в volume сохраняются).
infra-down:
	docker compose down

# Логи всех сервисов инфраструктуры.
infra-logs:
	docker compose logs -f

# Одна команда: поднять окружение и запустить приложение в профиле local.
dev-up: infra-up
	SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

# Загрузить dev-данные каталога (идемпотентно). Требует поднятого postgres и применённых миграций.
seed:
	docker compose exec -T postgres psql -U $${DB_USERNAME:-tzevabest} -d $${DB_NAME:-tzevabest} -v ON_ERROR_STOP=1 < src/main/resources/db/seed/dev_seed.sql
	@echo "Dev seed applied"

# Полная остановка (алиас infra-down).
down: infra-down

# Остановить и УДАЛИТЬ volume (postgres + minio) — данные будут потеряны.
clean:
	docker compose down -v

help:
	@echo "Tzeva Best — доступные команды:"
	@echo "  make dev-up      поднять инфраструктуру и запустить приложение (профиль local)"
	@echo "  make infra-up    только инфраструктура (docker compose up --wait)"
	@echo "  make infra-down  остановить инфраструктуру (volume сохраняются)"
	@echo "  make infra-logs  логи сервисов инфраструктуры"
	@echo "  make clean       остановить и удалить volume (данные будут потеряны)"
