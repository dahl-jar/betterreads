# syntax=docker/dockerfile:1

# Build stage: compile the fat jar with the Gradle wrapper.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Warm the dependency cache before copying sources so code changes do not
# re-download the world on every build.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
COPY config ./config
RUN ./gradlew --no-daemon clean bootJar -x test

# Runtime stage: JRE only, no build toolchain.
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home betterreads
COPY --from=build /src/build/libs/betterreads-backend-*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
