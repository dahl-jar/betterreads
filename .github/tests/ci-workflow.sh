#!/usr/bin/env bash
set -euo pipefail

readonly WORKFLOW=".github/workflows/ci.yml"
readonly JOB="jenkins"
readonly STEP="Send the test request"
readonly EXPECTED_IF="github.event_name == 'push' && !github.event.deleted"
readonly REPO="example-owner/app"
readonly REF="refs/heads/feature/x"
readonly SHA="0123456789abcdef0123456789abcdef01234567"
readonly QUEUE_URL="https://sqs.eu-north-1.amazonaws.com/000000000000/example-queue"
readonly MESSAGE_ID="00000000-0000-4000-8000-000000000001"
readonly TICK='`'
readonly EXPECTED_BODY='{"repo":"example-owner/app","ref":"refs/heads/feature/x","sha":"0123456789abcdef0123456789abcdef01234567","kind":"test"}'

fail() {
  printf 'ci-workflow.sh: %s\n' "$1" >&2
  exit 1
}

job_part() {
  uv run --quiet --no-project --with pyyaml==6.0.2 python3 -I - "${WORKFLOW}" "${JOB}" "${STEP}" "$1" \
    "github.ref=${REF}" "github.sha=${SHA}" "vars.DEPLOY_QUEUE_URL=${QUEUE_URL}" <<'PY'
import re
import sys

import yaml

workflow, job_name, step_name, part, *pairs = sys.argv[1:]
job = yaml.safe_load(open(workflow))["jobs"].get(job_name)
if job is None:
    sys.exit(f"{workflow} has no job {job_name}")
if part == "if":
    sys.stdout.write(job.get("if", ""))
    sys.exit(0)
step = next((step for step in job["steps"] if step.get("name") == step_name), None)
if step is None:
    sys.exit(f"job {job_name} has no step {step_name}")
if part == "run":
    sys.stdout.write(step["run"])
    sys.exit(0)
context = dict(pair.split("=", 1) for pair in pairs)
for key, value in step["env"].items():
    match = re.fullmatch(r"\$\{\{ ([\w.]+) \}\}", str(value))
    if match and match.group(1) not in context:
        sys.exit(f"step env {key} reads {value}, which the test does not supply")
    sys.stdout.write(f"{key}={context[match.group(1)] if match else value}\0")
PY
}

check_condition() {
  local actual
  actual="$(job_part if)"
  if [[ "${actual}" != "${EXPECTED_IF}" ]]; then
    fail "job ${JOB} runs if '${actual}', expected '${EXPECTED_IF}'"
  fi
}

write_fake_aws() {
  local bin="$1"
  cat > "${bin}/aws" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$@" > "${FAKE_AWS_ARGS}"
if [[ " $* " == *" --query MessageId --output text "* ]]; then
  printf '%s\n' "${FAKE_MESSAGE_ID}"
else
  jq -n --arg id "${FAKE_MESSAGE_ID}" '{MessageId: $id}'
fi
SH
  chmod +x "${bin}/aws"
}

arg_after() {
  local flag="$1"
  local file="$2"
  awk -v flag="${flag}" 'found { print; exit } $0 == flag { found = 1 }' "${file}"
}

clean_scratch() {
  rm -f "${scratch}/bin/aws" "${scratch}/args" "${scratch}/summary" "${scratch}/env"
  rmdir "${scratch}/bin" "${scratch}"
}

check_send_step() {
  local env_pairs pair run sent_body expected_summary
  write_fake_aws "${scratch}/bin"
  : > "${scratch}/summary"
  job_part env > "${scratch}/env"
  env_pairs=()
  while IFS= read -r -d '' pair; do
    env_pairs+=("${pair}")
  done < "${scratch}/env"
  run="$(job_part run)"
  env "${env_pairs[@]}" PATH="${scratch}/bin:${PATH}" GITHUB_REPOSITORY="${REPO}" \
    GITHUB_STEP_SUMMARY="${scratch}/summary" FAKE_AWS_ARGS="${scratch}/args" FAKE_MESSAGE_ID="${MESSAGE_ID}" \
    bash -c "${run}" || fail "step '${STEP}' exited non-zero"
  if [[ "$(arg_after --queue-url "${scratch}/args")" != "${QUEUE_URL}" ]]; then
    fail "step '${STEP}' sends to '$(arg_after --queue-url "${scratch}/args")', expected '${QUEUE_URL}'"
  fi
  sent_body="$(arg_after --message-body "${scratch}/args")"
  if ! jq -e --argjson sent "${sent_body}" --argjson expected "${EXPECTED_BODY}" -n '$sent == $expected' > /dev/null; then
    fail "step '${STEP}' sends '${sent_body}', expected '${EXPECTED_BODY}'"
  fi
  expected_summary="Sent ${TICK}${sent_body}${TICK}"$'\n\n'"MessageId ${TICK}${MESSAGE_ID}${TICK}"
  if [[ "$(cat "${scratch}/summary")" != "${expected_summary}" ]]; then
    fail "step '${STEP}' writes summary '$(cat "${scratch}/summary")', expected the body and MessageId ${MESSAGE_ID}"
  fi
}

scratch="$(mktemp -d "${TMPDIR:-/tmp}/ci-workflow.XXXXXX")"
mkdir "${scratch}/bin"
trap clean_scratch EXIT
check_condition
check_send_step
printf 'ci-workflow.sh: job %s condition, message body and step summary match\n' "${JOB}"
