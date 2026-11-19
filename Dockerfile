# syntax=docker/dockerfile:1

# ===== Build =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package

# ===== Runtime =====
FROM eclipse-temurin:24-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/*.jar app.jar

RUN mkdir -p /app/uploads && chown -R 1001:0 /app/uploads

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENV APP_UPLOADS_DIR=/app/uploads

USER 1001
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
