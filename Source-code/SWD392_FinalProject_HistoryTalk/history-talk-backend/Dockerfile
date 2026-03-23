# =============================================================================
# STAGE 1: BUILD
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# --- Layer Caching Optimization ---
# Copy ONLY the pom.xml first to download dependencies.
# This layer is cached and won't re-run unless pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# --- Build the application ---
# Copy source code AFTER dependencies are cached.
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# =============================================================================
# STAGE 2: RUNTIME
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

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
