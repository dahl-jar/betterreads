# Dependency-Check Suppression Policy

This directory holds the OWASP Dependency-Check suppression policy for BetterReads.

## Default stance

- Do not suppress a finding unless it is a verified false positive or a consciously time-boxed accepted risk.
- Prefer upgrading, replacing, or removing the dependency over suppressing the finding.
- Keep suppressions narrow, reviewable, and temporary whenever possible.

## Required rules for every suppression

- Add a clear reason in `<notes>`.
- Use the narrowest matcher possible.
- Use `until` for temporary suppressions.
- Link the suppression to a ticket, issue, or remediation plan in `<notes>`.

## Matcher priority

Use these in order of preference:

1. `sha1`
2. `gav`
3. `packageUrl`
4. exact `filePath`
5. regex `filePath` only as a last resort

Never suppress by broad CVSS threshold or broad regex just to make the build pass.

## Allowed cases

- false positive caused by incorrect CPE matching
- temporary risk acceptance while waiting for an upstream fix
- dependency is not reachable in the shipped runtime and the exception is documented

## Not allowed

- permanent suppressions with no explanation
- wildcard-style suppressions that hide unrelated future findings
- suppressing a vulnerability that can be fixed by a safe dependency upgrade already available
- adding unused suppressions

## Build enforcement

- `build.gradle.kts` wires `config/dependency-check/suppressions.xml`
- `failBuildOnUnusedSuppressionRule = true` is enabled
- `NVD_API_KEY` should be configured in CI and local environments when available

## Review policy

- review suppressions on every dependency update
- remove expired suppressions immediately
- remove temporary suppressions as soon as the dependency is upgraded
