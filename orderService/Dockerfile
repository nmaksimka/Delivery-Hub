FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY settings.gradle.kts ./
COPY build-logic ./build-logic
COPY apiContracts ./apiContracts
COPY orderService ./orderService

RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew :orderService:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /app/orderService/build/libs/*.jar app.jar
USER app
EXPOSE 3332
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
