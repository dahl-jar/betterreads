#!/usr/bin/env bash
#
# Retry-launch an Always Free VM.Standard.A1.Flex instance (2 OCPU / 12 GB)
# in eu-amsterdam-1 until OCI returns capacity. Loops forever; Ctrl-C to quit.
#
# Outputs one line per attempt: timestamp | attempt# | status | detail.

set -u

REGION="eu-amsterdam-1"
AD="ruTb:eu-amsterdam-1-AD-1"
SHAPE="VM.Standard.A1.Flex"
OCPUS=2
MEMORY_GB=12
DISPLAY_NAME="betterreads-a1"
SUBNET_OCID="ocid1.subnet.oc1.eu-amsterdam-1.aaaaaaaapw3l2qudakawswcywteripsb2xtsb6esdhrmruwlsgd6pua5pkkq"
IMAGE_OCID="ocid1.image.oc1.eu-amsterdam-1.aaaaaaaam4hd2kuvxgvfmugnycsdjihbkjgiorvhkdtdhj6bjnlq5rpmgtfa"
SSH_KEY="$HOME/.ssh/oci_betterreads.pub"
COMPARTMENT_OCID="$(awk -F= '/^compartment-id/ {gsub(/ /,"",$2); print $2}' "$HOME/.oci/oci_cli_rc")"

SLEEP_BETWEEN=600
LOG_FILE="$(dirname "$0")/launch-a1-flex.log"

ts() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }

log() {
  local line
  line="$(ts) | attempt=$1 | status=$2 | $3"
  printf '%s\n' "$line"
  printf '%s\n' "$line" >> "$LOG_FILE"
}

trap 'log "-" "exit" "interrupted by user"; exit 130' INT TERM

attempt=0
while :; do
  attempt=$((attempt + 1))
  out=$(oci compute instance launch \
    --region "$REGION" \
    --availability-domain "$AD" \
    --compartment-id "$COMPARTMENT_OCID" \
    --shape "$SHAPE" \
    --shape-config "{\"ocpus\": $OCPUS, \"memoryInGBs\": $MEMORY_GB}" \
    --display-name "$DISPLAY_NAME" \
    --image-id "$IMAGE_OCID" \
    --subnet-id "$SUBNET_OCID" \
    --assign-public-ip true \
    --ssh-authorized-keys-file "$SSH_KEY" \
    --wait-for-state RUNNING \
    --query 'data.{Id:id,State:"lifecycle-state"}' \
    --output json 2>&1)
  rc=$?

  if [[ $rc -eq 0 ]]; then
    log "$attempt" "SUCCESS" "$(printf '%s' "$out" | tr -d '\n' | head -c 400)"
    exit 0
  fi

  reason=$(printf '%s' "$out" | grep -oE '"code": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
  [[ -z "$reason" ]] && reason=$(printf '%s' "$out" | tr -d '\n' | head -c 200)
  log "$attempt" "FAIL" "$reason"

  sleep "$SLEEP_BETWEEN"
done
