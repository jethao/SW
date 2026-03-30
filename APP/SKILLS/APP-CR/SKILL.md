---
name: app-cr
description: Review AirHealth mobile app pull requests by inspecting open PRs in the `jethao/SW` repository that affect `APP`, checking the implementation against the linked Linear ticket in the `APP` team, requesting changes when the code or PR does not accurately satisfy the ticket, and approving the PR when the implementation and ticket alignment both look correct.
---

# App Code Review

Use this skill when Codex should act as the mobile app reviewer for AirHealth pull requests.

The purpose of this skill is to review open app PRs, verify that each PR correctly implements the linked Linear ticket, and take the appropriate review action:

- request changes when something is incorrect, incomplete, risky, or mismatched
- approve when the implementation accurately matches the ticket and the code looks sound

## Review Scope

Review open PRs for the SW repository:

- repository: `https://github.com/jethao/SW`
- app area of interest: `APP`
- Linear team: `APP`
- Linear team view reference: `https://linear.app/airhealth/team/APP/all`

Prefer PRs that change files under:

- `APP/`
- especially `APP/android`, `APP/ios`, and `APP/shared`

If a PR is open against the SW repo but does not materially touch app scope, skip it.

## Hard Rules

- Review only open PRs.
- Before making any GitHub or Linear review action call, gather as much review context as possible first: PR metadata, body, diff, touched files, linked ticket details, current review state, existing comments, and any needed surrounding app code.
- Do not approve a PR unless the linked Linear ticket is clear and the implementation matches it.
- If the PR has no linked ticket, treat that as incomplete review context and request changes unless the user explicitly says otherwise.
- If the linked ticket and code disagree, the ticket is the source of truth for review scope unless the PR description clearly documents an approved scope change.
- Request changes for correctness problems, missing requirements, broken behavior, missing verification, or obvious ticket mismatch.
- Approve only when the implementation, tests, and ticket alignment all look right.

## Required Inputs

Read from GitHub:

- open PR metadata for `jethao/SW`
- PR description
- changed files and diff
- existing review state when useful

Read from Linear:

- the linked issue referenced by PR title, body, branch name, or explicit URL
- parent story or epic only when needed for scope clarification
- dependency or blocker context when it affects review correctness

Read from the repo when needed:

- changed files under `SW/APP` or `APP`
- surrounding source needed to validate behavior
- tests or build files touched by the PR

## Ticket Link Rules

Treat these as valid ticket-link signals:

- explicit Linear issue URL in the PR body
- Linear issue key such as `APP-123` in the PR title
- Linear issue key such as `APP-123` in the branch name
- GitHub to Linear integration metadata when available

If multiple tickets are referenced:

- prefer the one explicitly described as the implementation target
- if the PR appears to combine multiple implementation tickets, request changes unless the user explicitly asked for bundled delivery

## Review Workflow

Follow this sequence.

### 1. Build The Review Queue

- list open PRs in `jethao/SW`
- filter to PRs that touch app paths or are clearly app tickets
- collect current review comments and state before deciding whether a new review action is needed
- review one PR at a time unless the user explicitly asks for a batch summary

### 2. Resolve The Linked Ticket

For each PR:

- find the linked Linear ticket
- open the ticket
- read the objective, scope, acceptance criteria, blockers, and notes
- read existing PR comments or review threads when they affect current review state or prior findings

If the ticket cannot be found or is ambiguous, do not approve the PR.

### 3. Compare PR Against Ticket Intent

Check whether the PR:

- implements the ticket’s actual requested behavior
- avoids unrelated scope creep
- touches the right app modules
- keeps the change small enough for the ticket size
- respects any stated blockers or dependencies

Request changes when the PR:

- misses required behavior
- implements the wrong behavior
- solves only part of the ticket without saying so
- contains major unrelated changes
- appears to proceed despite an unresolved blocker

### 4. Review Code Quality And Risk

Check for:

- behavioral regressions
- incorrect state handling
- navigation or routing mismatch
- entitlement, session, pairing, or history edge-case regressions where relevant
- platform-specific correctness issues on iOS or Android
- missing or weak tests for the changed behavior
- obvious maintainability problems that make the ticket unsafe to merge

Prioritize correctness and risk over style.

### 5. Check Verification

Confirm whether the PR includes or reports the right verification for the ticket, such as:

- unit tests
- integration tests
- UI or route-state checks
- platform build checks
- simulator or device evidence when the ticket calls for it

Missing verification is a reason to request changes when the ticket clearly needs it.

### 6. Take Review Action

If there are material issues:

- make sure the request-for-changes comment reflects the full gathered context rather than only the first missing detail discovered
- request changes
- summarize the concrete problems
- tie each problem back to the ticket or code behavior

If everything looks correct:

- approve the PR
- leave a short approval note that confirms ticket alignment and validation confidence

## Review Standard

Approve only when all of the following are true:

- linked ticket is identified
- ticket scope is implemented accurately
- no unresolved blocker should have prevented the work
- code changes are coherent and app-appropriate
- verification is appropriate for the risk
- no material correctness concerns remain

Request changes if any of these fail.

## Commenting Guidance

When requesting changes:

- lead with the most important correctness issue
- be explicit about what is wrong
- cite the ticket expectation that is not met
- keep comments actionable and specific

When approving:

- keep the message short
- mention the linked ticket key
- mention that implementation and verification appear aligned

## Final Output

For each reviewed PR, report:

- PR number and title
- linked Linear ticket
- decision: approved, requested changes, or skipped
- the main findings
- any residual risk

Keep the summary concise and review-focused.
