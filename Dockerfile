# ── Сборка ────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Сначала только файлы сборки — кэш зависимостей переживает изменения исходников.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true
COPY src ./src
# Тесты гоняет CI (нужен Docker для Testcontainers) — в образе только сборка jar.
RUN ./gradlew --no-daemon clean bootJar -x test

# ── Рантайм ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
# Непривилегированный пользователь.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
# Образ по умолчанию стартует в prod-профиле (fail-closed, аудит п.1): забытый
# SPRING_PROFILES_ACTIVE не поднимет контейнер в local с открытым API и dev-секретами.
# Для отладки можно переопределить: docker run -e SPRING_PROFILES_ACTIVE=local ...
ENV SPRING_PROFILES_ACTIVE=prod
# JVM под контейнер: heap как доля памяти контейнера.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
