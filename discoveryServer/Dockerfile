FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY settings.gradle.kts ./
COPY build-logic ./build-logic
COPY discoveryServer ./discoveryServer

RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew :discoveryServer:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /app/discoveryServer/build/libs/*.jar app.jar
USER app
EXPOSE 8761
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
