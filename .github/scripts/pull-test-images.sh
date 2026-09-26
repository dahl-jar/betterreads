#!/usr/bin/env bash
set -euo pipefail

readonly MAX_ATTEMPTS=5
readonly BACKOFF_SECONDS="${PULL_BACKOFF_SECONDS:-15}"

images=$(grep -rhoE 'DockerImageName\.parse\("[^"]+"\)' src | sed -E 's/.*\("([^"]+)"\)/\1/' | sort -u)

if [ -z "$images" ]; then
  echo "no Testcontainers images found under src" >&2
  exit 1
fi

pull_with_retry() {
  local image="$1"
  local attempt
  for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    if docker pull --quiet "$image"; then
      return 0
    fi
    echo "pull of $image failed (attempt $attempt of $MAX_ATTEMPTS)" >&2
    if [ "$attempt" -lt "$MAX_ATTEMPTS" ]; then
      sleep $((attempt * BACKOFF_SECONDS))
    fi
  done
  return 1
}

while IFS= read -r image; do
  pull_with_retry "$image" || { echo "could not pull $image" >&2; exit 1; }
done <<< "$images"
