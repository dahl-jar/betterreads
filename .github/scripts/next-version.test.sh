#!/usr/bin/env bash
set -uo pipefail

source "$(dirname "$0")/next-version.sh"
set +e

failures=0

report() {
  if [ "$2" = "$3" ]; then
    echo "ok - $1"
  else
    echo "not ok - $1: expected '$2', got '$3'"
    failures=$((failures + 1))
  fi
}

expect_output() {
  local name="$1" expected="$2"
  shift 2
  report "$name" "$expected" "$(next_version "$@" 2>/dev/null)"
}

expect_refusal() {
  local name="$1"
  shift
  if next_version "$@" >/dev/null 2>&1; then
    report "$name" "refusal" "a version"
  else
    report "$name" "refusal" "refusal"
  fi
}

new_folder() {
  mktemp -d "${TMPDIR:-/tmp}/next-version.XXXXXX"
}

commit_empty() {
  git -C "$1" -c user.name=test -c user.email=test@example.com -c commit.gpgsign=false commit -q --allow-empty -m "$2"
}

new_repo() {
  local repo
  repo="$(new_folder)" || exit 1
  [ -d "$repo" ] || exit 1
  git -C "$repo" init -q -b trunk
  commit_empty "$repo" one
  git -C "$repo" tag v1.0.0
  commit_empty "$repo" two
  echo "$repo"
}

read_previous() {
  (cd "$1" && previous_release_tag)
}

tag_head_and_read() {
  local repo
  repo="$(new_repo)" || exit 1
  git -C "$repo" tag v1.0.1
  read_previous "$repo"
}

tag_side_branch_and_read() {
  local repo
  repo="$(new_repo)" || exit 1
  git -C "$repo" checkout -q -b side HEAD~1
  commit_empty "$repo" side
  git -C "$repo" tag v1.0.5
  git -C "$repo" checkout -q trunk
  read_previous "$repo"
}

run_without_temp_folder() {
  local caller status
  caller="$(new_folder)" || exit 1
  git -C "$caller" init -q -b trunk
  commit_empty "$caller" base
  (cd "$caller" && TMPDIR="$caller/missing" tag_head_and_read > /dev/null 2>&1)
  status=$?
  echo "status=$status commits=$(git -C "$caller" rev-list --all --count) tags=$(git -C "$caller" tag | wc -l | tr -d ' ')"
}

expect_output "should give the next patch with the short commit" "1.0.1-4b2c9f9" v1.0.0 4b2c9f9
expect_output "should prefix a digit-only hash that starts with zero" "1.0.1-g0123456" v1.0.0 0123456
expect_output "should keep a hash that starts with zero and has letters" "1.0.1-0a12345" v1.0.0 0a12345
expect_refusal "should refuse when no tag exists" "" 4b2c9f9
expect_refusal "should refuse a tag that is not vMAJOR.MINOR.PATCH" v1.0 4b2c9f9
report "should pick the newest release tag before HEAD" "v1.0.0" "$(tag_side_branch_and_read)"
report "should skip a release tag on the commit itself" "v1.0.0" "$(tag_head_and_read)"
report "should stop before git when no temporary folder can be made" "status=1 commits=1 tags=0" "$(run_without_temp_folder)"

exit "$((failures > 0))"
