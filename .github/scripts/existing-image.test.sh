#!/usr/bin/env bash
set -uo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
readonly SCRIPT="$script_dir/existing-image.sh"
readonly MAIN_DIGEST='sha256:1111111111111111111111111111111111111111111111111111111111111111'
readonly RELEASE_DIGEST='sha256:2222222222222222222222222222222222222222222222222222222222222222'
readonly COMMIT_SHA='0123456789012345678901234567890123456789'
readonly MAIN_TAG='1.0.1-g0123456'
readonly RELEASE_TAG='v1.0.1'
readonly NO_MAIN_IMAGE='no main image for this commit, rerun after the main run'
readonly ACCESS_DENIED='An error occurred (AccessDeniedException) when calling the DescribeImages operation: not authorized'

failures=0

report() {
  if [ "$2" = "$3" ]; then
    echo "ok - $1"
  else
    echo "not ok - $1: expected '$2', got '$3'"
    failures=$((failures + 1))
  fi
}

write_stub() {
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

new_registry() {
  local folder
  folder="$(mktemp -d "${TMPDIR:-/tmp}/existing-image.XXXXXX")" || exit 1
  [ -d "$folder" ] || exit 1
  mkdir "$folder/images"
  write_stub "$folder"
  echo "$folder"
}

add_image() {
  echo "$3" > "$1/images/$2"
}

run_script() {
  local folder="$1" ref_type="$2" tag="$3"
  STUB_DIR="$folder" PATH="$folder:$PATH" REPOSITORY=betterreads TAG="$tag" GITHUB_REF_TYPE="$ref_type" GITHUB_SHA="$COMMIT_SHA" \
    bash "$SCRIPT" > "$folder/out" 2> "$folder/err"
  echo "$?" > "$folder/status"
}

existing_tag() {
  local folder
  folder="$(new_registry)"
  add_image "$folder" "$MAIN_TAG" "$MAIN_DIGEST"
  run_script "$folder" branch "$MAIN_TAG"
  cat "$folder/out"
}

new_branch_image() {
  local folder
  folder="$(new_registry)"
  add_image "$folder" "$MAIN_TAG" "$MAIN_DIGEST"
  run_script "$folder" branch 1.0.2-0a12345
  echo "status=$(cat "$folder/status") output=$(cat "$folder/out")"
}

release_retag() {
  local folder
  folder="$(new_registry)"
  add_image "$folder" "$MAIN_TAG" "$MAIN_DIGEST"
  run_script "$folder" tag "$RELEASE_TAG"
  grep -c -- "put-image --repository-name betterreads --image-tag $RELEASE_TAG" "$folder/calls.log"
}

release_without_main_image() {
  local folder
  folder="$(new_registry)"
  add_image "$folder" 1.0.1-0a12345 "$RELEASE_DIGEST"
  run_script "$folder" tag "$RELEASE_TAG"
  echo "status=$(cat "$folder/status") error=$(cat "$folder/err")"
}

other_error() {
  local folder
  folder="$(new_registry)"
  echo "$ACCESS_DENIED" > "$folder/error"
  run_script "$folder" branch "$MAIN_TAG"
  echo "status=$(cat "$folder/status") error=$(cat "$folder/err")"
}

report "should print the digest of an existing tag" "digest=$MAIN_DIGEST" "$(existing_tag)"
report "should find nothing for a new branch image" "status=0 output=" "$(new_branch_image)"
report "should add the release tag to the main image" "1" "$(release_retag)"
report "should fail a tag push without a main image" "status=1 error=$NO_MAIN_IMAGE" "$(release_without_main_image)"
report "should fail on another AWS error" "status=1 error=$ACCESS_DENIED" "$(other_error)"

exit "$((failures > 0))"
