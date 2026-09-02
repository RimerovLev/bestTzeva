#!/usr/bin/env bash
# T-14.6: восстановление БД в ОТДЕЛЬНОЕ окружение из снапшота или логического дампа.
# НИКОГДА не запускать против прод-БД. Ожидаемое время — см. docs/disaster-recovery.md.
set -euo pipefail

MODE="${1:-help}"   # snapshot | logical
: "${TARGET_PGHOST:?target (восстановительное) окружение}" "${TARGET_PGDATABASE:?}"

case "${MODE}" in
  snapshot)
    : "${SNAPSHOT_ID:?}" "${RESTORE_INSTANCE:?}"
    echo "[restore] RDS restore-db-instance-to-point-in-time / from-snapshot ${SNAPSHOT_ID} → ${RESTORE_INSTANCE}"
    aws rds restore-db-instance-from-db-snapshot \
      --db-instance-identifier "${RESTORE_INSTANCE}" --db-snapshot-identifier "${SNAPSHOT_ID}"
    echo "[restore] ждём available, затем smoke: select count(*) from flyway_schema_history;"
    ;;
  logical)
    : "${DUMP_S3_URI:?s3://bucket/logical/<stamp>/critical.sql.gz}"
    echo "[restore] logical restore ${DUMP_S3_URI} → ${TARGET_PGHOST}/${TARGET_PGDATABASE}"
    aws s3 cp "${DUMP_S3_URI}" - | gunzip | psql -h "${TARGET_PGHOST}" -d "${TARGET_PGDATABASE}"
    ;;
  *)
    echo "usage: restore.sh snapshot|logical  (переменные окружения — в шапке скрипта)"; exit 1;;
esac
echo "[restore] завершено; зафиксировать фактические RTO/RPO в docs/disaster-recovery.md"
