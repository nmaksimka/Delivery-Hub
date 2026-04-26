FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY buildSrc ./buildSrc
COPY apiContracts ./apiContracts
COPY userService ./userService
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew :userService:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/userService/build/libs/*.jar app.jar
EXPOSE 3334
ENTRYPOINT ["java", "-jar", "/app/app.jar"]