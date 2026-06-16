# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY settings.gradle.kts build.gradle.kts ./
COPY buildSrc ./buildSrc
COPY apiContracts ./apiContracts
COPY userService ./userService

# Кэш Gradle переиспользуется между сборками (BuildKit), иначе каждый из пяти
# образов заново скачивал бы весь classpath.
RUN --mount=type=cache,target=/root/.gradle ./gradlew :userService:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /app/userService/build/libs/*.jar app.jar
USER app
EXPOSE 3334
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
