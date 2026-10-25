# Build context: repository root.  docker build -f deploy/backend.Dockerfile .
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
# Small Docker VMs (Colima defaults to 2 GiB, no swap) OOM-kill an uncapped Gradle build next to a running stack.
RUN mkdir -p /root/.gradle \
    && echo 'org.gradle.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=320m -XX:+UseSerialGC' > /root/.gradle/gradle.properties
COPY backend/gradle gradle
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.lockfile ./
RUN ./gradlew -q --no-daemon dependencies
COPY backend/src src
RUN ./gradlew -q --no-daemon bootJar && cp build/libs/learning-portal-*.jar /app.jar

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
