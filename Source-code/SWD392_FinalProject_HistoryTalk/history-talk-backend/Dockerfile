# =============================================================================
# STAGE 1: BUILD
# =============================================================================
# --- Build stage ---
# --- Stage 1: Build stage (Dùng image có sẵn Maven) ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# 1. Chỉ copy pom.xml để cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copy source code và build file JAR
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Runtime stage (Giữ nguyên như cũ) ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# --- Security: Run as non-root user ---
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Change ownership to the non-root user
RUN chown spring:spring /app/app.jar

USER spring

# Application port (must match server.port in application.properties)
EXPOSE 8080

# --- JVM Tuning & Environment Variables ---
# Environment variables below are placeholders; actual values are injected
# at runtime by AWS App Runner (or docker run -e ...).
# DB Configuration
ENV DB_URL=""
ENV DB_USER=""
ENV DB_PASSWORD=""
ENV DB_SCHEMA=""
# JWT Configuration
ENV JWT_SECRET=""
ENV JWT_EXPIRATION_MS=""
ENV JWT_REFRESH_EXPIRATION_MS=""

# ENTRYPOINT with JVM memory limits to prevent App Runner OOM kills
ENTRYPOINT ["java", \
  "-Xms256m", \
  "-Xmx512m", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
