# syntax=docker/dockerfile:1

# ---- Build: Gradle wrapper on JDK 21 (build.gradle toolchain = 21) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper + build scripts first so dependency downloads are cached between builds.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar$')" /workspace/app.jar

# ---- Run: slim JRE 21 ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app \
 && mkdir -p /app/uploads && chown -R app:app /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar
USER app

# Tuned for a 512 MB container (Render free plan). The JVM reads JAVA_TOOL_OPTIONS by itself,
# so it can be overridden from the Render dashboard without rebuilding.
# Heap is capped at 65% of RAM to leave room for metaspace, code cache and thread stacks.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+ExitOnOutOfMemoryError" \
    APP_UPLOAD_DIR=/app/uploads \
    PORT=8080

# Render sets PORT; application.yml maps server.port to ${PORT:${SERVER_PORT:8080}}.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
