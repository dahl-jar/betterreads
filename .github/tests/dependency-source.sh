#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT="dependency-source.sh"
readonly JENKINS_FLAG="--jenkins"
readonly SETTINGS="settings.gradle.kts"
readonly CODEARTIFACT_URL_LINE='s|^val codeartifactUrl = "https://\([^/"]*\)/.*|\1|p'
readonly MAVEN_CENTRAL_HOST="repo.maven.apache.org"
readonly PLUGIN_PORTAL_HOST="plugins.gradle.org"
readonly MAVEN_CENTRAL_NAME="MavenRepo"
readonly PLUGIN_PORTAL_NAME="Gradle Central Plugin Repository"
readonly MAVEN_CENTRAL_URL="https://${MAVEN_CENTRAL_HOST}/maven2"
readonly PLUGIN_PORTAL_URL="https://${PLUGIN_PORTAL_HOST}/m2"
readonly METADATA="gradle/verification-metadata.xml"
readonly DRY_RUN_METADATA="gradle/verification-metadata.dryrun.xml"
readonly LOCKFILE="gradle.lockfile"
readonly BUILDSCRIPT_LOCKFILE="buildscript-gradle.lockfile"
readonly COMPONENTS_RANGE="/<components>/,/<\\/components>/"
readonly WRAPPER_PROPERTIES="gradle/wrapper/gradle-wrapper.properties"
readonly WRAPPER_DIST="gradle-9.4.0-bin"
readonly WEBSEARCH_NPMRC="docker/websearch/.npmrc"
readonly WEBSEARCH_PACKAGE="docker/websearch/package.json"
readonly PACKAGE_MANAGER_LINE='s/^ *"packageManager": *"\([^"]*\)".*/\1/p'
readonly NPM_PACKAGE="is-number@7.0.0"
readonly NPM_PACKAGE_DIR="is-number"
readonly FETCH_401="ERR_PNPM_FETCH_401"
readonly ENV_WARNING="Failed to replace env"
readonly INSTALL_SUMMARY="a websearch install without a token fails at CodeArtifact, "
readonly TOOL_PINS_SCRIPT=".github/tests/tool_pins.py"
readonly MIRROR_SCRIPT=".github/tests/mirror.py"
readonly JSOUP="org.jsoup:jsoup:1.23.2"
readonly LOCKED_JSOUP="implementation(\"${JSOUP}\")"
readonly UNLOCKED_JSOUP='implementation("org.jsoup:jsoup:1.24.1")'
readonly JSOUP_JAR="jsoup-1.23.2.jar"
readonly LOCKED_PLUGIN='id("net.ltgt.errorprone") version "5.1.0"'
readonly UNLOCKED_PLUGIN='id("net.ltgt.errorprone") version "5.1.1"'
readonly UNLOCKED_PLUGIN_MODULE="net.ltgt.errorprone.gradle.plugin:5.1.1"
readonly LOCK_REFUSAL="Dependency version enforced by Dependency Locking"
readonly LOCKED_VERSION_REFUSAL="Did not resolve '${JSOUP}' which has been forced / substituted to a different version: '1.24.1'"
readonly MISSING_LOCK_STATE="is locked but does not have lock state"
readonly VERIFICATION_FAILURE="Dependency verification failed"
readonly UNKNOWN_SOURCE="pubilc"
readonly RELATIVE_MIRROR="mirror"
readonly EMPTY_MIRROR="empty-mirror"
readonly MIRROR_WITH_PUBLIC_SOURCE="dependencySource 'public' and dependencyMirrorDir are both set, set only one"
readonly RELATIVE_MIRROR_REFUSAL="dependencyMirrorDir must be an absolute path, got '${RELATIVE_MIRROR}'"
readonly EMPTY_TOKEN_WARNING="CODEARTIFACT_AUTH_TOKEN is empty: set it to a CodeArtifact authorization token"
readonly ZERO_CHECKSUM="0000000000000000000000000000000000000000000000000000000000000000"
readonly PROBE_CONFIGURATION="lockProbe"
readonly GUAVA="com.google.guava:guava:33.7.2-jre"
readonly HTTP_CLIENT_PINS="org.apache.httpcomponents.client5:httpclient5:5.6.3 org.apache.httpcomponents.core5:httpcore5:5.4.3 org.apache.httpcomponents.core5:httpcore5-h2:5.4.3"
readonly PINS=(
  "cpd ${GUAVA} org.jline:jline:3.25.0 org.jsoup:jsoup:1.23.2 org.mozilla:rhino:1.7.15.1 ${HTTP_CLIENT_PINS}"
  "pmd ${HTTP_CLIENT_PINS}"
  "checkstyle ${GUAVA} org.codehaus.plexus:plexus-utils:3.6.1"
  "annotationProcessor ${GUAVA}"
  "testAnnotationProcessor ${GUAVA}"
  "liveTestAnnotationProcessor ${GUAVA}"
  "localDbVerificationAnnotationProcessor ${GUAVA}"
  "runtimeClasspath org.json:json:20260719"
)
readonly CASES=(
  check_wrapper_pins_the_distribution_checksum
  check_websearch_npmrc_pins_codeartifact
  check_unknown_source_is_refused
  check_mirror_is_the_only_source
  check_mirror_with_public_source_is_refused
  check_relative_mirror_is_refused
  check_default_source_is_codeartifact
  check_empty_token_is_named
  check_unlocked_version_is_refused
  check_unlocked_plugin_is_refused
  check_unlocked_configuration_is_refused
  check_changed_checksum_is_refused
  check_public_source_matches_the_metadata
  check_pins_clear_the_advisories
  check_offline_build_from_mirror_passes
)
readonly MAC_CASES=(
  check_websearch_install_without_token_fails
)

root="$(cd "$(dirname "$0")/../.." && pwd)"
scratch="$(mktemp -d)"
wrapper_dists="${GRADLE_USER_HOME:-${HOME}/.gradle}/wrapper/dists"
codeartifact_host="$(sed -n "${CODEARTIFACT_URL_LINE}" "${root}/${SETTINGS}")"
readonly root scratch wrapper_dists codeartifact_host
trap 'rm -rf "${scratch}"' EXIT

fail() {
  printf '%s: %s\n' "${SCRIPT}" "$1" >&2
  exit 1
}

[[ -n "${codeartifact_host}" ]] || fail "${SETTINGS} has no codeartifactUrl line"

seed_wrapper() {
  local home="$1"
  mkdir -p "${home}/wrapper/dists"
  if [[ -d "${wrapper_dists}/${WRAPPER_DIST}" ]]; then
    cp -R "${wrapper_dists}/${WRAPPER_DIST}" "${home}/wrapper/dists/"
  fi
}

gradle_in() {
  local home="$1"
  shift
  (cd "${scratch}/repo" && env -u CODEARTIFACT_AUTH_TOKEN -u ORG_GRADLE_PROJECT_dependencyMirrorDir \
    -u ORG_GRADLE_PROJECT_dependencySource GRADLE_USER_HOME="${home}" \
    ./gradlew --no-daemon --console=plain "$@" 2>&1)
}

restore() {
  cp "${root}/$1" "${scratch}/repo/$1"
}

expect_failure() {
  local name="$1"
  local home="$2"
  shift 2
  if output="$(gradle_in "${home}" "$@")"; then
    fail "${name}: gradle $* passed, expected a failure"
  fi
}

expect_text() {
  local name="$1"
  local text="$2"
  if [[ "${output}" != *"${text}"* ]]; then
    printf '%s\n' "${output}" >&2
    fail "${name}: output does not contain '${text}'"
  fi
}

expect_no_text() {
  local name="$1"
  local text="$2"
  if [[ "${output}" == *"${text}"* ]]; then
    printf '%s\n' "${output}" >&2
    fail "${name}: output contains '${text}'"
  fi
}

expect_only_mirror() {
  local name="$1"
  expect_text "${name}" "dependencyMirror(file:"
  expect_text "${name}" "${EMPTY_MIRROR}"
  expect_no_text "${name}" "codeartifact"
  expect_no_public_source "${name}"
}

expect_no_public_source() {
  local name="$1"
  local text
  for text in "${MAVEN_CENTRAL_HOST}" "${PLUGIN_PORTAL_HOST}" "${MAVEN_CENTRAL_NAME}" "${PLUGIN_PORTAL_NAME}"; do
    expect_no_text "${name}" "${text}"
  done
}

set_up() {
  mkdir -p "${scratch}/repo" "${scratch}/${EMPTY_MIRROR}"
  (cd "${root}" && tar --exclude=./build --exclude=./.gradle --exclude=./.git --exclude=./.local \
    --exclude=./docker/websearch/node_modules -cf - .) | tar -xf - -C "${scratch}/repo"
  rm -f "${scratch}/repo/${DRY_RUN_METADATA}"
  local home
  for home in gradle-home public-home mirror-home default-home offline-home; do
    seed_wrapper "${scratch}/${home}"
  done
}

check_wrapper_pins_the_distribution_checksum() {
  local url pinned published
  url="$(sed -n 's/^distributionUrl=//p' "${root}/${WRAPPER_PROPERTIES}" | sed 's/\\:/:/g')"
  pinned="$(sed -n 's/^distributionSha256Sum=//p' "${root}/${WRAPPER_PROPERTIES}")"
  published="$(curl -fsSL "${url}.sha256")"
  if [[ "${pinned}" != "${published}" ]]; then
    fail "${WRAPPER_PROPERTIES} pins '${pinned}', ${url} publishes '${published}'"
  fi
}

check_websearch_npmrc_pins_codeartifact() {
  local expected actual
  expected="$(printf '%s\n' \
    "registry=https://${codeartifact_host}/npm/npm/" \
    "//${codeartifact_host}/npm/npm/:_authToken=\${CODEARTIFACT_AUTH_TOKEN:-unset}" \
    "minimum-release-age=4320")"
  actual="$(cat "${root}/${WEBSEARCH_NPMRC}")"
  if [[ "${actual}" != "${expected}" ]]; then
    fail "${WEBSEARCH_NPMRC} is '${actual}', expected '${expected}'"
  fi
}

check_websearch_install_without_token_fails() {
  local manager dir="${scratch}/websearch"
  manager="$(sed -n "${PACKAGE_MANAGER_LINE}" "${root}/${WEBSEARCH_PACKAGE}")"
  [[ -n "${manager}" ]] || fail "${WEBSEARCH_PACKAGE} has no packageManager line"
  mkdir -p "${dir}"
  cp "${root}/${WEBSEARCH_NPMRC}" "${dir}/.npmrc"
  printf '{"name": "websearch-check", "private": true, "packageManager": "%s"}\n' "${manager}" >"${dir}/package.json"
  if output="$(cd "${dir}" && env -u CODEARTIFACT_AUTH_TOKEN pnpm add --save-exact "${NPM_PACKAGE}" \
    --store-dir "${dir}/store" 2>&1)"; then
    fail "websearch-install: pnpm add with CODEARTIFACT_AUTH_TOKEN unset passed, expected ${FETCH_401}"
  fi
  expect_text websearch-install "${FETCH_401}"
  expect_text websearch-install "${codeartifact_host}"
  expect_no_text websearch-install "${ENV_WARNING}"
  if [[ -e "${dir}/node_modules/${NPM_PACKAGE_DIR}" ]]; then
    fail "websearch-install: pnpm add with CODEARTIFACT_AUTH_TOKEN unset left node_modules/${NPM_PACKAGE_DIR}"
  fi
}

check_unknown_source_is_refused() {
  expect_failure unknown-source "${scratch}/gradle-home" "-PdependencySource=${UNKNOWN_SOURCE}" help
  expect_text unknown-source "dependencySource must be 'public' or unset, got '${UNKNOWN_SOURCE}'"
}

check_mirror_is_the_only_source() {
  expect_failure mirror-only "${scratch}/mirror-home" --offline "-PdependencyMirrorDir=${scratch}/${EMPTY_MIRROR}" help
  expect_only_mirror mirror-only
}

check_mirror_with_public_source_is_refused() {
  expect_failure mirror-with-public "${scratch}/mirror-home" "-PdependencyMirrorDir=${scratch}/${EMPTY_MIRROR}" \
    -PdependencySource=public help
  expect_text mirror-with-public "${MIRROR_WITH_PUBLIC_SOURCE}"
}

check_relative_mirror_is_refused() {
  expect_failure relative-mirror "${scratch}/mirror-home" --offline "-PdependencyMirrorDir=${RELATIVE_MIRROR}" help
  expect_text relative-mirror "${RELATIVE_MIRROR_REFUSAL}"
}

check_default_source_is_codeartifact() {
  expect_failure default-source "${scratch}/default-home" --info help
  expect_text default-source "HTTP 401: https://${codeartifact_host}/maven/maven/"
  expect_no_public_source default-source
}

check_empty_token_is_named() {
  expect_failure empty-token "${scratch}/default-home" help
  expect_text empty-token "${EMPTY_TOKEN_WARNING}"
}

check_unlocked_version_is_refused() {
  sed -i.bak "s|${LOCKED_JSOUP}|${UNLOCKED_JSOUP}|" "${scratch}/repo/build.gradle.kts"
  expect_failure unlocked-version "${scratch}/gradle-home" -PdependencySource=public \
    --dependency-verification lenient compileJava
  restore build.gradle.kts
  expect_text unlocked-version "${LOCKED_VERSION_REFUSAL}"
}

check_unlocked_plugin_is_refused() {
  sed -i.bak "s|${LOCKED_PLUGIN}|${UNLOCKED_PLUGIN}|" "${scratch}/repo/build.gradle.kts"
  expect_failure unlocked-plugin "${scratch}/gradle-home" -PdependencySource=public \
    --dependency-verification lenient help
  restore build.gradle.kts
  expect_text unlocked-plugin "${UNLOCKED_PLUGIN_MODULE}"
  expect_text unlocked-plugin "${LOCK_REFUSAL}"
}

check_unlocked_configuration_is_refused() {
  printf '\nval %s by configurations.creating\ndependencies { %s("%s") }\n' \
    "${PROBE_CONFIGURATION}" "${PROBE_CONFIGURATION}" "${JSOUP}" >>"${scratch}/repo/build.gradle.kts"
  expect_failure unlocked-configuration "${scratch}/gradle-home" -PdependencySource=public \
    dependencies --configuration "${PROBE_CONFIGURATION}"
  restore build.gradle.kts
  expect_text unlocked-configuration "${PROBE_CONFIGURATION}"
  expect_text unlocked-configuration "${MISSING_LOCK_STATE}"
}

check_changed_checksum_is_refused() {
  python3 -I - "${scratch}/repo/${METADATA}" "${JSOUP_JAR}" "${ZERO_CHECKSUM}" <<'PY'
import re
import sys

path, artifact, checksum = sys.argv[1:]
text = open(path).read()
pattern = re.compile(r'(<artifact name="' + re.escape(artifact) + r'">\s*<sha256 value=")[0-9a-f]{64}')
changed, count = pattern.subn(lambda match: match.group(1) + checksum, text)
if count != 1:
    sys.exit(f"{path} has {count} sha256 entries for {artifact}, expected 1")
open(path, "w").write(changed)
PY
  expect_failure changed-checksum "${scratch}/gradle-home" -PdependencySource=public compileJava
  restore "${METADATA}"
  expect_text changed-checksum "${VERIFICATION_FAILURE}"
  expect_text changed-checksum "${JSOUP_JAR}"
}

check_public_source_matches_the_metadata() {
  sed -i.bak "${COMPONENTS_RANGE}d" "${scratch}/repo/${METADATA}"
  if ! output="$(gradle_in "${scratch}/public-home" -PdependencySource=public \
    --write-verification-metadata sha256 --dry-run dependencies check bootJar)"; then
    printf '%s\n' "${output}" >&2
    fail "public-source: the dry run failed"
  fi
  if ! diff "${scratch}/repo/${DRY_RUN_METADATA}" "${root}/${METADATA}" >&2; then
    fail "public-source: ${DRY_RUN_METADATA} differs from ${METADATA}"
  fi
  rm "${scratch}/repo/${DRY_RUN_METADATA}"
  restore "${METADATA}"
}

check_pins_clear_the_advisories() {
  if ! output="$(gradle_in "${scratch}/gradle-home" -PdependencySource=public \
    --dependency-verification lenient --write-locks dependencies)"; then
    printf '%s\n' "${output}" >&2
    fail "pins: gradle --write-locks dependencies failed"
  fi
  printf '%s\n' "${output}" > "${scratch}/dependencies.txt"
  local status=0
  python3 -I "${root}/${TOOL_PINS_SCRIPT}" "${scratch}/dependencies.txt" "${root}/${LOCKFILE}" \
    "${scratch}/repo/${LOCKFILE}" "${PINS[@]}" || status=$?
  restore "${LOCKFILE}"
  restore "${BUILDSCRIPT_LOCKFILE}"
  [[ "${status}" -eq 0 ]] || fail "pins: a pinned version does not hold"
}

check_offline_build_from_mirror_passes() {
  python3 -I "${root}/${MIRROR_SCRIPT}" "${root}/${METADATA}" "${scratch}/mirror" "${MAVEN_CENTRAL_URL}" \
    "${PLUGIN_PORTAL_URL}" || fail "offline-build: the mirror is incomplete"
  if ! output="$(gradle_in "${scratch}/offline-home" --offline "-PdependencyMirrorDir=${scratch}/mirror" \
    check bootJar)"; then
    printf '%s\n' "${output}" >&2
    fail "offline-build: the offline build from the mirror failed"
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
  set_up
  local check ran=0 install=""
  local cases=("${CASES[@]}")
  if [[ "${jenkins}" == false ]]; then
    cases+=("${MAC_CASES[@]}")
    install="${INSTALL_SUMMARY}"
  fi
  for check in "${cases[@]}"; do
    "${check}"
    ran=$((ran + 1))
  done
  if [[ "${jenkins}" == true ]]; then
    printf '%s: Jenkins mode, %d cases\n' "${SCRIPT}" "${ran}"
  fi
  printf '%s\n' "${SCRIPT}: the wrapper and websearch pins hold, ${install}the mirror is the only source, a mirror with the public source, an unknown source, a relative mirror, an unlocked version, plugin or configuration and a changed checksum fail, the default source is CodeArtifact, the public source matches the metadata, the pins hold, and an offline build from the mirror passes"
}

main "$@"
