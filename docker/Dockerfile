# syntax=docker/dockerfile:1

FROM --platform=linux/amd64 node:26-bookworm-slim@sha256:5df79989ed685e09f0a98019db4cebfb22fb9df1a53c0161664a011f7ff6b0f1 AS node

FROM --platform=linux/amd64 gcr.io/distroless/java25-debian13:debug-nonroot@sha256:447b37137b3f9a65d624940fae9171e801297411b849fd2252d998f283a6b1d4 AS shell

FROM --platform=linux/amd64 gcr.io/distroless/java25-debian13:nonroot@sha256:1c4ced761d17961c900cba60c33c8c3c46edbdadaa9d5ef9806eb5a6824fd406
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
