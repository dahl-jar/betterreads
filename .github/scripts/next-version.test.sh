#!/usr/bin/env bash
set -uo pipefail

source "$(dirname "$0")/next-version.sh"
source "$(dirname "$0")/report.sh"
set +e

expect_output() {
  local name="$1" expected="$2"
  shift 2
  report "$name" "$expected" "$(next_version "$@" 2>/dev/null)"
}

expect_refusal() {
  local name="$1" message="$2" error status
  shift 2
  error="$(next_version "$@" 2>&1 >/dev/null)"
  status=$?
  report "$name" "status=1 error=$message" "status=$status error=$error"
}

commit_empty() {
  git -C "$1" -c user.name=test -c user.email=test@example.com -c commit.gpgsign=false commit -q --allow-empty -m "$2"
}

new_repo() {
  local repo
  repo="$(mktemp -d "${TMPDIR:-/tmp}/next-version.XXXXXX")" || exit 1
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

tag_ancestor_and_read() {
  local repo
  repo="$(new_repo)" || exit 1
  git -C "$repo" tag "$1"
  commit_empty "$repo" three
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

expect_output "should give the next patch with the short commit" "1.0.1-4b2c9f9" v1.0.0 4b2c9f9
expect_output "should prefix a digit-only hash that starts with zero" "1.0.1-g0123456" v1.0.0 0123456
expect_output "should keep a hash that starts with zero and has letters" "1.0.1-0a12345" v1.0.0 0a12345
expect_output "should keep a digit-only hash without a leading zero" "1.0.1-1234567" v1.0.0 1234567
expect_refusal "should refuse when no tag exists" \
  "no vMAJOR.MINOR.PATCH tag before this commit, create a baseline tag" "" 4b2c9f9
expect_refusal "should refuse a tag that is not vMAJOR.MINOR.PATCH" \
  "tag v1.0 is not vMAJOR.MINOR.PATCH" v1.0 4b2c9f9
expect_refusal "should refuse a hash that is not 7 lowercase hex characters" \
  "short hash 4B2C9F is not 7 lowercase hex characters" v1.0.0 4B2C9F
report "should pick the newest merged release tag" "v1.0.2" "$(tag_ancestor_and_read v1.0.2)"
report "should skip a v tag that is not vMAJOR.MINOR.PATCH" "v1.0.0" "$(tag_ancestor_and_read v1.1-beta)"
report "should skip a release tag on another branch" "v1.0.0" "$(tag_side_branch_and_read)"
report "should skip a release tag on the commit itself" "v1.0.0" "$(tag_head_and_read)"

exit "$((failures > 0))"
