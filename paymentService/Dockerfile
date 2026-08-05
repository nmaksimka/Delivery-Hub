FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY settings.gradle.kts ./
COPY build-logic ./build-logic
COPY paymentService ./paymentService

RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew :paymentService:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /app/paymentService/build/libs/*.jar app.jar
USER app
EXPOSE 3336
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
