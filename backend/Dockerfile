# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app \
    && mkdir -p /app/data/files \
    && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/project-management-*.jar app.jar

USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
