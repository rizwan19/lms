# Build context: repository root.  docker build -f deploy/backend.Dockerfile .
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q -DskipTests package && cp target/learning-portal-*.jar /app.jar

FROM eclipse-temurin:21-jre-noble
# ffprobe validates uploaded videos (no transcoding); curl serves the container health check.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin learning \
    && mkdir -p /app /data/media /data/staging \
    && chown -R learning:learning /data
COPY --from=build /app.jar /app/app.jar
USER learning
ENV LEARNING_MEDIA_ROOT=/data/media \
    LEARNING_STAGING_DIR=/data/staging
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
