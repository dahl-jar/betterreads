#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT="runtime-image.sh"
readonly JENKINS_FLAG="--jenkins"
readonly DOCKERFILE="docker/Dockerfile"
readonly JAR="build/libs/app.jar"
readonly PLAIN_JAR="build/libs/betterreads-backend-0.0.1-SNAPSHOT-plain.jar"
readonly CLAUDE="docker/websearch/node_modules/@anthropic-ai/claude-code-linux-x64/claude"
readonly SEARCH_HOOK="docker/websearch/restrict-search.mjs"
readonly JAR_TEXT="boot-jar-stub"
readonly PLAIN_JAR_TEXT="plain-jar-stub"
readonly CLAUDE_TEXT="claude-stub"
readonly ARCHITECTURE="amd64"
readonly ELF_64_BIT_MAGIC="7f454c4602"
readonly ELF_X86_64_MACHINE="3e00"
readonly ELF_MACHINE_OFFSET=36
readonly ELF_HEADER_BYTES=20
readonly PACKAGE_MANAGERS='gradlew|gradle |pnpm|npm |npx|corepack'
readonly TOKEN_MARKERS='codeartifact|_authToken'
readonly PROBE_BASE="busybox:1.37.0@sha256:a7dbc319b1438115a207e11930dc4bb3e643dcb3ec0bdd694bb36cd8fad289ec"
readonly PROBE_ROOT="/c"
readonly RUNTIME_FILES="app/app.jar
opt/websearch/restrict-search.mjs
usr/bin/sh
usr/local/bin/claude
usr/local/bin/node"
readonly HOOK_SETTINGS='{"hooks":{"SessionStart":[{"hooks":[{"type":"command","command":"node /opt/websearch/restrict-search.mjs || exit 2"}]}]}}'
readonly HOOK_OUTPUT="expected 'SessionStart' but got 'PreToolUse'"
readonly HOOK_NOT_FOUND="status code 127"
readonly CLAUDE_DEBUG="/tmp/claude/debug"
readonly JENKINS_CASES=(check_dockerfile_runs_no_package_manager)
readonly CASES=(
  check_dockerfile_runs_no_package_manager
  check_image_copies_the_boot_jar
  check_image_is_x86_64_with_the_context_claude
  check_image_holds_no_token
  check_context_has_only_the_runtime_files
  check_image_adds_only_the_runtime_files
  check_hook_runs_through_claude
)

root="$(cd "$(dirname "$0")/../.." && pwd)"
scratch="$(mktemp -d)"
readonly root scratch
readonly image="runtime-image-check:$$"
readonly probe_image="runtime-image-check-context:$$"
readonly hook_image="runtime-image-check-hook:$$"

fail() {
  printf '%s: %s\n' "${SCRIPT}" "$1" >&2
  exit 1
}

clean_up() {
  if command -v docker > /dev/null; then
    docker image rm -f "${image}" "${probe_image}" "${hook_image}" > /dev/null 2>&1 || true
  fi
  rm -rf "${scratch}"
}

trap clean_up EXIT

make_context() {
  local context="$1"
  mkdir -p "${context}/build/libs"
  (cd "${root}" && tar --exclude=./build --exclude=./.gradle --exclude=./.git --exclude=./.local \
    --exclude=./docker/websearch/node_modules -cf - .) | tar -xf - -C "${context}"
  mkdir -p "${context}/$(dirname "${CLAUDE}")"
  printf '%s' "${JAR_TEXT}" > "${context}/${JAR}"
  printf '%s' "${PLAIN_JAR_TEXT}" > "${context}/${PLAIN_JAR}"
  printf '%s' "${CLAUDE_TEXT}" > "${context}/${CLAUDE}"
  chmod 0755 "${context}/${CLAUDE}"
}

build() {
  local context="$1"
  local tag="$2"
  docker build --quiet -f "${context}/${DOCKERFILE}" -t "${tag}" "${context}" 2>&1
}

copy_out() {
  local container
  container="$(docker create "$1")"
  docker cp "${container}:$2" "$3" > /dev/null
  docker rm "${container}" > /dev/null
}

image_file() {
  copy_out "${image}" "$1" "${scratch}/image-file"
  cat "${scratch}/image-file"
}

expect_x86_64_elf() {
  local path="$1"
  local header
  copy_out "${image}" "${path}" "${scratch}/elf-file"
  header="$(head -c "${ELF_HEADER_BYTES}" "${scratch}/elf-file" | od -An -tx1 | tr -d ' \n')"
  if [[ "${header}" != "${ELF_64_BIT_MAGIC}"* || "${header:${ELF_MACHINE_OFFSET}:4}" != "${ELF_X86_64_MACHINE}" ]]; then
    fail "${path} has the ELF header ${header}, expected a 64-bit ELF for machine ${ELF_X86_64_MACHINE}"
  fi
}

check_dockerfile_runs_no_package_manager() {
  local instructions
  instructions="$(sed -e ':join' -e '/\\$/N' -e 's/\\\n//' -e 't join' "${root}/${DOCKERFILE}" | grep -E '^RUN ' || true)"
  if grep -Eq "${PACKAGE_MANAGERS}" <<< "${instructions}"; then
    fail "${DOCKERFILE} runs a package manager: $(grep -E "${PACKAGE_MANAGERS}" <<< "${instructions}")"
  fi
}

check_image_copies_the_boot_jar() {
  make_context "${scratch}/context"
  build "${scratch}/context" "${image}" > /dev/null || fail "the image build from the stub context failed"
  local jar
  jar="$(image_file /app/app.jar)"
  if [[ "${jar}" != "${JAR_TEXT}" ]]; then
    fail "/app/app.jar is '${jar}', expected '${JAR_TEXT}'"
  fi
}

check_image_is_x86_64_with_the_context_claude() {
  local architecture
  architecture="$(docker image inspect --format '{{.Architecture}}' "${image}")"
  if [[ "${architecture}" != "${ARCHITECTURE}" ]]; then
    fail "the image is ${architecture}, expected ${ARCHITECTURE}"
  fi
  expect_x86_64_elf /usr/local/bin/node
  copy_out "${image}" /usr/local/bin/claude "${scratch}/claude"
  if [[ "$(tail -c "${#CLAUDE_TEXT}" "${scratch}/claude")" != "${CLAUDE_TEXT}" ]]; then
    fail "/usr/local/bin/claude is not the file from ${CLAUDE}"
  fi
}

check_image_holds_no_token() {
  if docker history --no-trunc --format '{{.CreatedBy}}' "${image}" | grep -E "${TOKEN_MARKERS}" > /dev/null; then
    fail "the image history names codeartifact or _authToken"
  fi
  local container
  container="$(docker create "${image}")"
  if docker export "${container}" | grep -aE "${TOKEN_MARKERS}" > /dev/null; then
    docker rm "${container}" > /dev/null
    fail "the image filesystem contains codeartifact or _authToken"
  fi
  docker rm "${container}" > /dev/null
}

check_context_has_only_the_runtime_files() {
  make_context "${scratch}/probe-context"
  printf 'FROM %s\nCOPY . %s\n' "${PROBE_BASE}" "${PROBE_ROOT}" > "${scratch}/probe.Dockerfile"
  docker build --quiet -f "${scratch}/probe.Dockerfile" -t "${probe_image}" "${scratch}/probe-context" > /dev/null 2>&1 \
    || fail "the context probe build failed"
  local expected actual
  expected="$(printf '%s\n' "${PROBE_ROOT}/${JAR}" "${PROBE_ROOT}/${CLAUDE}" "${PROBE_ROOT}/${SEARCH_HOOK}" | sort)"
  actual="$(docker run --rm "${probe_image}" find "${PROBE_ROOT}" -type f | sort)"
  if [[ "${actual}" != "${expected}" ]]; then
    fail "the build context has '${actual//$'\n'/, }', expected '${expected//$'\n'/, }'"
  fi
}

regular_files() {
  local container
  container="$(docker create --platform "linux/${ARCHITECTURE}" "$1")"
  docker export "${container}" | tar -tvf - | awk '$1 ~ /^-/ { print $NF }' | sort
  docker rm "${container}" > /dev/null
}

check_image_adds_only_the_runtime_files() {
  local base added
  base="$(grep -E '^FROM ' "${root}/${DOCKERFILE}" | tail -1 | awk '{ print $NF }')"
  regular_files "${base}" > "${scratch}/base-files"
  regular_files "${image}" > "${scratch}/image-files"
  added="$(comm -13 "${scratch}/base-files" "${scratch}/image-files")"
  if [[ "${added}" != "${RUNTIME_FILES}" ]]; then
    fail "the image adds '${added//$'\n'/, }' to ${base}, expected '${RUNTIME_FILES//$'\n'/, }'"
  fi
}

check_hook_runs_through_claude() {
  [[ -f "${root}/${CLAUDE}" ]] || fail "${CLAUDE} is missing: install docker/websearch first"
  make_context "${scratch}/hook-context"
  cp "${root}/${CLAUDE}" "${scratch}/hook-context/${CLAUDE}"
  build "${scratch}/hook-context" "${hook_image}" > /dev/null || fail "the image build with the real claude binary failed"
  local container log
  container="$(docker create --entrypoint /usr/local/bin/claude "${hook_image}" -p probe --setting-sources "" \
    --settings "${HOOK_SETTINGS}" --debug hooks --output-format json)"
  docker start -a "${container}" > /dev/null 2>&1 || true
  docker cp "${container}:${CLAUDE_DEBUG}" "${scratch}/hook-debug" > /dev/null
  docker rm "${container}" > /dev/null
  log="$(cat "${scratch}"/hook-debug/*.txt)"
  if [[ "${log}" == *"${HOOK_NOT_FOUND}"* || "${log}" != *"${HOOK_OUTPUT}"* ]]; then
    fail "claude did not run the websearch hook in the image: $(grep -h 'Hook SessionStart' "${scratch}"/hook-debug/*.txt)"
  fi
}

main() {
  local jenkins=false
  case "${1-}" in
    "") ;;
    "${JENKINS_FLAG}") jenkins=true ;;
    *) fail "unknown argument $1, expected ${JENKINS_FLAG} or nothing" ;;
  esac
  [[ $# -le 1 ]] || fail "unknown argument $2, expected ${JENKINS_FLAG} or nothing"
  local check ran=0
  if [[ "${jenkins}" == true ]]; then
    for check in "${JENKINS_CASES[@]}"; do
      "${check}"
      ran=$((ran + 1))
    done
    printf '%s: Jenkins mode, %d cases\n' "${SCRIPT}" "${ran}"
    return
  fi
  for check in "${CASES[@]}"; do
    "${check}"
  done
  printf '%s\n' "${SCRIPT}: the image runs no package manager, copies the boot jar and the context claude binary into an amd64 image, holds no token, gets only the runtime files, adds only the runtime files to the base image, and runs the websearch hook through claude"
}

main "$@"
