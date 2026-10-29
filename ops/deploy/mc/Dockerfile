# MinIO client (mc) из официального публичного бинарника — для одноразового создания бакетов.
FROM alpine:3.20
RUN apk add --no-cache ca-certificates bash curl \
 && curl -fsSL https://dl.min.io/client/mc/release/linux-arm64/mc -o /usr/bin/mc \
 && chmod +x /usr/bin/mc
ENTRYPOINT ["mc"]
