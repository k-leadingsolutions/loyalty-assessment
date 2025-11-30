# ===== Build stage =====

FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# Copy root and module POMs so Maven can resolve reactor coordinates
COPY pom.xml .
COPY loyalty-service/pom.xml loyalty-service/pom.xml
COPY loyalty-integration/pom.xml loyalty-integration/pom.xml
COPY loyalty-contracts/pom.xml loyalty-contracts/pom.xml

# Maven cache for the module
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl loyalty-service -am dependency:go-offline

# Copy module source
COPY loyalty-service/src loyalty-service/src

# Switch into the module directory so dependency:copy-dependencies writes to
# /build/loyalty-service/target/dependency predictably.
WORKDIR /build/loyalty-service

# Build module and copy runtime dependency jars into target/dependency
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package dependency:copy-dependencies -DoutputDirectory=target/dependency

# ===== Runtime stage =====
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Install curl for healthcheck
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd --create-home --shell /bin/bash appuser


COPY --from=build /build/loyalty-service/target/classes /app/classes
COPY --from=build /build/loyalty-service/target/dependency /app/lib

RUN chown -R appuser:appuser /app && chmod -R 0555 /app

EXPOSE 8888
ENV JAVA_OPTS="-Xms256m -Xmx512m" HTTP_PORT=8888

USER appuser

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:${HTTP_PORT}/health || exit 1

# Use Vert.x Launcher to run your verticle by FQN (no Main class required)
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dhttp.port=${HTTP_PORT} -cp '/app/classes:/app/lib/*' io.vertx.core.Launcher run com.kleadingsolutions.loyalty.MainVerticle"]