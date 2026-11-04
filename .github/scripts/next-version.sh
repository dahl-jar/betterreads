#!/usr/bin/env bash
set -euo pipefail

readonly RELEASE_TAG_PATTERN='^v([0-9]+)\.([0-9]+)\.([0-9]+)$'
readonly SHORT_HASH_PATTERN='^[0-9a-f]{7}$'
readonly DIGITS_PATTERN='^[0-9]+$'
readonly HASH_PREFIX='g'

previous_release_tag() {
  git tag -l 'v*' --sort=-v:refname --merged HEAD --no-contains HEAD | grep -E "$RELEASE_TAG_PATTERN" | head -n 1 || true
}

next_version() {
  local tag="$1" hash="$2"
  if [ -z "$tag" ]; then
    echo "no vMAJOR.MINOR.PATCH tag before this commit, create a baseline tag" >&2
    return 1
  fi
  if [[ ! "$tag" =~ $RELEASE_TAG_PATTERN ]]; then
    echo "tag $tag is not vMAJOR.MINOR.PATCH" >&2
    return 1
  fi
  local major="${BASH_REMATCH[1]}" minor="${BASH_REMATCH[2]}" patch="${BASH_REMATCH[3]}"
  if [[ ! "$hash" =~ $SHORT_HASH_PATTERN ]]; then
    echo "short hash $hash is not 7 lowercase hex characters" >&2
    return 1
  fi
  if [[ "$hash" =~ $DIGITS_PATTERN && "$hash" == 0* ]]; then
    hash="${HASH_PREFIX}${hash}"
  fi
  printf '%s.%s.%s-%s\n' "$major" "$minor" "$((patch + 1))" "$hash"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  next_version "$(previous_release_tag)" "$1"
fi
