#!/usr/bin/env bash
set -uo pipefail

source "$(dirname "$0")/report.sh"

readonly REGISTRY_HOST='registry.example.com'
readonly REPOSITORY_NAME='example-repo'
readonly WORKFLOW_REPOSITORY='example-owner/example-repo'
readonly ISSUER='https://issuer.example.com'
readonly WORKFLOW="https://github.com/$WORKFLOW_REPOSITORY/.github/workflows/checks.yml"
readonly BRANCH_IDENTITY="$WORKFLOW@refs/heads/main"
readonly TAG_IDENTITY="$WORKFLOW@refs/tags/v1.0.1"
readonly MAIN_DIGEST='sha256:1111111111111111111111111111111111111111111111111111111111111111'
readonly RELEASE_DIGEST='sha256:2222222222222222222222222222222222222222222222222222222222222222'
readonly COMMIT_SHA='0123456789012345678901234567890123456789'
readonly LETTER_SHA='4b2c9f9012345678901234567890123456789012'
readonly OTHER_SHA='fedcba9876543210987654321098765432109876'
readonly MAIN_TAG='1.0.1-g0123456'
readonly LETTER_TAG='1.0.1-4b2c9f9'
readonly RELEASE_TAG='v1.0.1'
readonly NO_MAIN_IMAGE='no main image for this commit, rerun after the main run'
readonly UNSIGNED_MAIN="image $MAIN_TAG has no signature from a branch run of this commit, delete tag $MAIN_TAG and rerun"
readonly MOVED_RELEASE="tag $RELEASE_TAG is on another image than $MAIN_TAG, delete tag $RELEASE_TAG and rerun"
readonly ACCESS_DENIED='An error occurred (AccessDeniedException) when calling the DescribeImages operation: not authorized'

write_aws_stub() {
  cat > "$1/aws" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
echo "$*" >> "$STUB_DIR/calls.log"
if [ -f "$STUB_DIR/error" ]; then
  cat "$STUB_DIR/error" >&2
  exit 254
fi
command="$2"
image_tag=""
query=""
new_tag=""
manifest=""
while [ $# -gt 0 ]; do
  case "$1" in
    --image-ids) image_tag="${2#imageTag=}"; shift ;;
    --query) query="$2"; shift ;;
    --image-tag) new_tag="$2"; shift ;;
    --image-manifest) manifest="$2"; shift ;;
  esac
  shift
done
case "$command" in
  describe-images)
    if [ -z "$image_tag" ]; then
      for image in "$STUB_DIR"/images/*; do
        printf '%s\t' "${image##*/}"
      done
      echo
    elif [ -f "$STUB_DIR/images/$image_tag" ]; then
      cat "$STUB_DIR/images/$image_tag"
    else
      echo "An error occurred (ImageNotFoundException) when calling the DescribeImages operation: no image $image_tag" >&2
      exit 254
    fi
    ;;
  batch-get-image)
    if [[ "$query" == *MediaType ]]; then
      echo application/vnd.oci.image.manifest.v1+json
    else
      cat "$STUB_DIR/images/$image_tag"
    fi
    ;;
  put-image)
    echo "$manifest" > "$STUB_DIR/images/$new_tag"
    ;;
esac
STUB
  chmod +x "$1/aws"
}

write_cosign_stub() {
  cat > "$1/cosign" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
echo "cosign $*" >> "$STUB_DIR/calls.log"
[ "$1" = verify ] || exit 1
issuer=""
identity_regexp=""
repository=""
sha=""
while [ $# -gt 1 ]; do
  case "$1" in
    --certificate-oidc-issuer) issuer="$2"; shift ;;
    --certificate-identity-regexp) identity_regexp="$2"; shift ;;
    --certificate-github-workflow-repository) repository="$2"; shift ;;
    --certificate-github-workflow-sha) sha="$2"; shift ;;
  esac
  shift
done
digest="${1##*@}"
signature="$STUB_DIR/signatures/$digest"
[ "$1" = "$REGISTRY/$REPOSITORY@$digest" ] && [ -n "$identity_regexp" ] && [ -f "$signature" ] || exit 1
read -r signed_issuer signed_identity signed_repository signed_sha < "$signature"
[ "$signed_issuer" = "$issuer" ] && [[ "$signed_identity" =~ $identity_regexp ]] && [ "$signed_repository" = "$repository" ] && [ "$signed_sha" = "$sha" ]
STUB
  chmod +x "$1/cosign"
}

new_registry() {
  local folder
  folder="$(mktemp -d "${TMPDIR:-/tmp}/find-or-retag-image.XXXXXX")" || exit 1
  [ -d "$folder" ] || exit 1
  mkdir "$folder/images" "$folder/signatures" || exit 1
  write_aws_stub "$folder" || exit 1
  write_cosign_stub "$folder" || exit 1
  [ -x "$folder/aws" ] && [ -x "$folder/cosign" ] || exit 1
  echo "$folder"
}

add_image() {
  echo "$3" > "$1/images/$2"
}

add_signature() {
  echo "$ISSUER $3 $WORKFLOW_REPOSITORY $4" > "$1/signatures/$2"
}

add_signed_image() {
  add_image "$1" "$2" "$3"
  add_signature "$1" "$3" "$4" "$5"
}

outcome() {
  echo "status=$1 output=$2 error=$3 puts=$4"
}

run_script() {
  local folder="$1" ref_type="$2" tag="$3" sha="${4:-$COMMIT_SHA}" status
  STUB_DIR="$folder" PATH="$folder:$PATH" REGISTRY="$REGISTRY_HOST" REPOSITORY="$REPOSITORY_NAME" OIDC_ISSUER="$ISSUER" \
    GITHUB_REPOSITORY="$WORKFLOW_REPOSITORY" TAG="$tag" GITHUB_REF_TYPE="$ref_type" GITHUB_SHA="$sha" \
    bash "$(dirname "$0")/find-or-retag-image.sh" > "$folder/out" 2> "$folder/err"
  status=$?
  outcome "$status" "$(cat "$folder/out")" "$(cat "$folder/err")" "$(grep -c -- "put-image --repository-name $REPOSITORY_NAME " "$folder/calls.log")"
}

existing_tag() {
  local folder
  folder="$(new_registry)" || exit 1
  add_signed_image "$folder" "$MAIN_TAG" "$MAIN_DIGEST" "$1" "$2"
  run_script "$folder" branch "$MAIN_TAG"
}

new_branch_image() {
  local folder
  folder="$(new_registry)" || exit 1
  add_signed_image "$folder" "$MAIN_TAG" "$MAIN_DIGEST" "$BRANCH_IDENTITY" "$COMMIT_SHA"
  run_script "$folder" branch 1.0.2-0a12345
}

release_retag() {
  local folder
  folder="$(new_registry)" || exit 1
  add_signed_image "$folder" "$1" "$MAIN_DIGEST" "$BRANCH_IDENTITY" "$2"
  run_script "$folder" tag "$RELEASE_TAG" "$3"
}

existing_release_tag() {
  local folder
  folder="$(new_registry)" || exit 1
  add_signed_image "$folder" "$MAIN_TAG" "$MAIN_DIGEST" "$BRANCH_IDENTITY" "$COMMIT_SHA"
  add_image "$folder" "$RELEASE_TAG" "$1"
  run_script "$folder" tag "$RELEASE_TAG"
}

release_without_main_image() {
  local folder
  folder="$(new_registry)" || exit 1
  add_signed_image "$folder" 1.0.1-0a12345 "$RELEASE_DIGEST" "$BRANCH_IDENTITY" "$COMMIT_SHA"
  run_script "$folder" tag "$RELEASE_TAG"
}

other_error() {
  local folder
  folder="$(new_registry)" || exit 1
  echo "$ACCESS_DENIED" > "$folder/error"
  run_script "$folder" branch "$MAIN_TAG"
}

report "should print the digest of an existing tag signed for this commit" \
  "$(outcome 0 "digest=$MAIN_DIGEST" "" 0)" "$(existing_tag "$BRANCH_IDENTITY" "$COMMIT_SHA")"
report "should fail an existing tag signed for another commit" \
  "$(outcome 1 "" "$UNSIGNED_MAIN" 0)" "$(existing_tag "$BRANCH_IDENTITY" "$OTHER_SHA")"
report "should fail an existing tag signed only by a tag run" \
  "$(outcome 1 "" "$UNSIGNED_MAIN" 0)" "$(existing_tag "$TAG_IDENTITY" "$COMMIT_SHA")"
report "should find nothing for a new branch image" \
  "$(outcome 0 "" "" 0)" "$(new_branch_image)"
report "should add the release tag to the main image" \
  "$(outcome 0 "digest=$MAIN_DIGEST" "added tag $RELEASE_TAG to image $MAIN_TAG" 1)" \
  "$(release_retag "$MAIN_TAG" "$COMMIT_SHA" "$COMMIT_SHA")"
report "should add the release tag to a main image without the g prefix" \
  "$(outcome 0 "digest=$MAIN_DIGEST" "added tag $RELEASE_TAG to image $LETTER_TAG" 1)" \
  "$(release_retag "$LETTER_TAG" "$LETTER_SHA" "$LETTER_SHA")"
report "should not retag a main image signed for another commit" \
  "$(outcome 1 "" "$UNSIGNED_MAIN" 0)" "$(release_retag "$MAIN_TAG" "$OTHER_SHA" "$COMMIT_SHA")"
report "should print the digest of a release tag on the main image" \
  "$(outcome 0 "digest=$MAIN_DIGEST" "" 0)" "$(existing_release_tag "$MAIN_DIGEST")"
report "should fail a release tag on another image" \
  "$(outcome 1 "" "$MOVED_RELEASE" 0)" "$(existing_release_tag "$RELEASE_DIGEST")"
report "should fail a tag push without a main image" \
  "$(outcome 1 "" "$NO_MAIN_IMAGE" 0)" "$(release_without_main_image)"
report "should fail on another AWS error" \
  "$(outcome 1 "" "$ACCESS_DENIED" 0)" "$(other_error)"

exit "$((failures > 0))"
