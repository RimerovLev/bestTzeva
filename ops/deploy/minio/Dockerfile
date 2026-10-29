# MinIO-сервер из официального публичного бинарника (dl.min.io открыт; образы minio/minio на
# Docker Hub/quay закрыли в 2025). arm64 — под Oracle Ampere A1. Консоль не ставим (в свежих
# сборках её убрали) — нужен только S3 API на :9000.
FROM alpine:3.20
RUN apk add --no-cache ca-certificates curl \
 && curl -fsSL https://dl.min.io/server/minio/release/linux-arm64/minio -o /usr/bin/minio \
 && chmod +x /usr/bin/minio
EXPOSE 9000
ENTRYPOINT ["minio"]
