# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507 AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Resolve dependencies before copying sources so a source-only change reuses the cached layer.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
COPY config ./config
RUN ./gradlew --no-daemon clean bootJar -x test

FROM node:25-bookworm-slim@sha256:81db02c4b671288a03915da9534dbd54f96d0e7c24d80ccc54f5b36b2e684370 AS websearch
WORKDIR /opt/websearch
COPY docker/websearch/package.json docker/websearch/pnpm-lock.yaml ./
RUN corepack enable \
    && corepack prepare pnpm@10.15.0 --activate \
    && pnpm install --frozen-lockfile --prod --config.node-linker=hoisted

FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0
WORKDIR /app
ENV HOME=/tmp \
    CLAUDE_CONFIG_DIR=/tmp/claude
COPY --from=websearch /usr/local/bin/node /usr/local/bin/node
COPY --from=websearch /opt/websearch/node_modules/@anthropic-ai/claude-code-linux-x64/claude /usr/local/bin/claude
RUN sed -i 's|http://|https://|g' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get -o APT::Update::Error-Mode=any update \
    && apt-get install -y --only-upgrade apt libapt-pkg7.0 \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --no-create-home betterreads
COPY docker/websearch/restrict-search.mjs /opt/websearch/restrict-search.mjs
COPY --from=build /src/build/libs/betterreads-backend-*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
