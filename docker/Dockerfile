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

FROM node:22-bookworm-slim@sha256:43ac6c60b8f89723f746e8a92ce91abd5017e627ce1ddfe4238355d3a30b772c AS websearch
WORKDIR /opt/websearch
COPY docker/websearch/package.json docker/websearch/pnpm-lock.yaml ./
RUN corepack enable \
    && corepack prepare pnpm@10.15.0 --activate \
    && pnpm install --frozen-lockfile --prod --config.node-linker=hoisted

FROM eclipse-temurin:25-jre
WORKDIR /app
ENV HOME=/tmp \
    CLAUDE_CONFIG_DIR=/tmp/claude
COPY --from=websearch /usr/local/bin/node /usr/local/bin/node
COPY --from=websearch /opt/websearch/node_modules/@anthropic-ai/claude-code-linux-x64/claude /usr/local/bin/claude
RUN useradd --system --uid 10001 --no-create-home betterreads
COPY docker/websearch/restrict-search.mjs /opt/websearch/restrict-search.mjs
COPY --from=build /src/build/libs/betterreads-backend-*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
