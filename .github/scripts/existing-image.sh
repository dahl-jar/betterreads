#!/usr/bin/env bash
set -euo pipefail

readonly NOT_FOUND='ImageNotFoundException'

digest_of() {
  aws ecr describe-images --repository-name "$REPOSITORY" --image-ids "imageTag=$1" --query 'imageDetails[0].imageDigest' --output text
}

main_tag_of() {
  aws ecr describe-images --repository-name "$REPOSITORY" --query 'imageDetails[].imageTags[]' --output text | tr '\t' '\n' | grep -Ex "[0-9]+\.[0-9]+\.[0-9]+-g?$1" | head -n 1 || true
}

existing_image() {
  local answer
  if answer="$(digest_of "$TAG" 2>&1)"; then
    echo "digest=$answer"
    return 0
  fi
  if [[ "$answer" != *"$NOT_FOUND"* ]]; then
    echo "$answer" >&2
    return 1
  fi
  if [ "$GITHUB_REF_TYPE" != tag ]; then
    return 0
  fi
  local main_tag
  main_tag="$(main_tag_of "${GITHUB_SHA::7}")"
  if [ -z "$main_tag" ]; then
    echo "no main image for this commit, rerun after the main run" >&2
    return 1
  fi
  local manifest media_type
  manifest="$(aws ecr batch-get-image --repository-name "$REPOSITORY" --image-ids "imageTag=$main_tag" --query 'images[0].imageManifest' --output text)"
  media_type="$(aws ecr batch-get-image --repository-name "$REPOSITORY" --image-ids "imageTag=$main_tag" --query 'images[0].imageManifestMediaType' --output text)"
  aws ecr put-image --repository-name "$REPOSITORY" --image-tag "$TAG" --image-manifest "$manifest" --image-manifest-media-type "$media_type" > /dev/null
  echo "added tag $TAG to image $main_tag" >&2
  echo "digest=$(digest_of "$TAG")"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  existing_image
fi
