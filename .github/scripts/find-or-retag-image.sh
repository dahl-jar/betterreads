#!/usr/bin/env bash
set -euo pipefail

readonly NOT_FOUND='ImageNotFoundException'
readonly BRANCH_IDENTITY_REGEXP="^https://github.com/$GITHUB_REPOSITORY/.github/workflows/checks.yml@refs/heads/.+$"

digest_of() {
  aws ecr describe-images --repository-name "$REPOSITORY" --image-ids "imageTag=$1" --query 'imageDetails[0].imageDigest' --output text
}

branch_tag_of() {
  aws ecr describe-images --repository-name "$REPOSITORY" --query 'imageDetails[].imageTags[]' --output text | tr '\t' '\n' | grep -Ex "[0-9]+\.[0-9]+\.[0-9]+-g?$1" | head -n 1 || true
}

require_signed() {
  local tag="$1" digest="$2"
  if ! cosign verify --certificate-oidc-issuer "$OIDC_ISSUER" --certificate-identity-regexp "$BRANCH_IDENTITY_REGEXP" \
    --certificate-github-workflow-repository "$GITHUB_REPOSITORY" --certificate-github-workflow-sha "$GITHUB_SHA" \
    "$REGISTRY/$REPOSITORY@$digest" > /dev/null; then
    echo "image $tag has no signature from a branch run of this commit, delete tag $tag and rerun" >&2
    return 1
  fi
}

find_branch_image() {
  branch_tag="$(branch_tag_of "${GITHUB_SHA::7}")"
  if [ -z "$branch_tag" ]; then
    echo "no main image for this commit, rerun after the main run" >&2
    return 1
  fi
  branch_digest="$(digest_of "$branch_tag")"
  require_signed "$branch_tag" "$branch_digest"
}

require_found_image() {
  if [ "$GITHUB_REF_TYPE" != tag ]; then
    require_signed "$TAG" "$1"
    return
  fi
  find_branch_image
  if [ "$1" != "$branch_digest" ]; then
    echo "tag $TAG is on another image than $branch_tag, delete tag $TAG and rerun" >&2
    return 1
  fi
}

retag() {
  find_branch_image
  local manifest media_type
  manifest="$(aws ecr batch-get-image --repository-name "$REPOSITORY" --image-ids "imageTag=$branch_tag" --query 'images[0].imageManifest' --output text)"
  media_type="$(aws ecr batch-get-image --repository-name "$REPOSITORY" --image-ids "imageTag=$branch_tag" --query 'images[0].imageManifestMediaType' --output text)"
  aws ecr put-image --repository-name "$REPOSITORY" --image-tag "$TAG" --image-manifest "$manifest" --image-manifest-media-type "$media_type" > /dev/null
  echo "added tag $TAG to image $branch_tag" >&2
}

find_or_retag() {
  local answer
  if answer="$(digest_of "$TAG" 2>&1)"; then
    require_found_image "$answer"
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
  retag
  echo "digest=$branch_digest"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  find_or_retag
fi
