# syntax=docker/dockerfile:1

FROM --platform=linux/amd64 node:26-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68 AS node

FROM --platform=linux/amd64 gcr.io/distroless/java25-debian13:debug-nonroot@sha256:8c3f19b909de5e1e0f1e453b09cc4176cb8fb8e248a347414e554172f311b363 AS shell

FROM --platform=linux/amd64 gcr.io/distroless/java25-debian13:nonroot@sha256:8ac5525549d14b8ddd34562f9fc8e6ba4840c995363a1507b17cadf8bb7ab57e
WORKDIR /app
ENV HOME=/tmp \
    CLAUDE_CONFIG_DIR=/tmp/claude
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=shell /busybox/busybox /bin/sh
COPY docker/websearch/node_modules/@anthropic-ai/claude-code-linux-x64/claude /usr/local/bin/claude
COPY docker/websearch/restrict-search.mjs /opt/websearch/restrict-search.mjs
COPY build/libs/app.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
