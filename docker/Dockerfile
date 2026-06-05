# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Resolve dependencies before copying sources so a source-only change reuses the cached layer.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
COPY config ./config
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home betterreads
COPY --from=build /src/build/libs/betterreads-backend-*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
