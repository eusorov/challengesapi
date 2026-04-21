# syntax=docker/dockerfile:1

# --- Build: JDK + Gradle wrapper (tests skipped; run ./gradlew test in CI or locally)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# Dependency resolution layer (invalidates when Gradle files change)
RUN ./gradlew --no-daemon dependencies

COPY src src

# Gradle exit code is the primary success signal; also require a non-empty, readable JAR (zip).
RUN set -e \
    && ./gradlew --no-daemon bootJar -x test \
    && jar_path="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "$jar_path" \
    && test -s "$jar_path" \
    && jar tf "$jar_path" >/dev/null \
    && cp "$jar_path" /workspace/application.jar

# --- Runtime: JRE only
FROM eclipse-temurin:25-jre

RUN groupadd --system spring && useradd --system --gid spring spring
WORKDIR /app

COPY --from=build /workspace/application.jar /app/application.jar

RUN chown spring:spring /app/application.jar
USER spring:spring

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
