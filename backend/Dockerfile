# =============================================================================
# Git-To-Go Backend — Multi-stage Docker Build
# Stage 1: Maven build with dependency caching
# Stage 2: Minimal JRE runtime
# =============================================================================

# --------------- Stage 1: Build ---------------
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Copy Maven wrapper and POM first for dependency caching
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build the JAR (skip tests — they run in CI)
COPY src src
RUN ./mvnw package -DskipTests -B

# --------------- Stage 2: Runtime ---------------
FROM eclipse-temurin:17-jre

LABEL maintainer="git2go-team"
LABEL description="Git-To-Go Spring Boot Backend"

# Run as non-root for security
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

# Copy only the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# The backend needs access to Docker socket (mounted at runtime),
# so appuser must be in the docker group. The GID is set at runtime
# via docker-compose to match the host's docker group.
# For now, we run as root to allow Docker socket access.
# In production, consider a more fine-grained approach.
USER root

EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
