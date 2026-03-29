# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Gradle wrapper
COPY gradlew gradlew
COPY gradle/ gradle/
RUN chmod +x gradlew

# Cache dependencies — only reruns if build.gradle changes
COPY build.gradle settings.gradle* gradle.properties* ./
RUN ./gradlew dependencies --no-daemon --configuration compileClasspath || true

# Build fat JAR
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /app/build/libs/*.jar app.jar

RUN mkdir -p logs && chown -R spring:spring /app
USER spring

EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
