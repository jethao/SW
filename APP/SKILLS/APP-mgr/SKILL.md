---
name: app-mgr
description: Manage the AirHealth mobile delivery loop by orchestrating `APP-ENG` for the `APP` Linear team and `jethao/SW` app pull requests. Use when Codex should first reconcile backlog parent tickets based on sub-ticket completion, then pick unblocked backlog work, invoke `APP-ENG` to implement ready app tickets, stop starting new work whenever app PRs are open, repeatedly invoke `APP-ENG` to address PR review comments until review is complete, and refresh `SW/APP/manager.rpt` on every run.
---

# App Manager

Use this skill when Codex should act as the mobile work manager for AirHealth rather than as the individual implementer.

The purpose of this skill is to coordinate the mobile delivery loop safely:

1. reconcile backlog parent tickets against their sub-ticket completion state
2. select only ready mobile tickets with no unresolved blockers
3. invoke `APP-ENG` to implement ready tickets and open PRs
4. stop starting new work while app PRs are open
5. wait for Codex PR review to complete
6. repeatedly invoke `APP-ENG` until all actionable PR comments are addressed
7. refresh the manager report with the current state

## Managed Skills

This skill coordinates:

- `APP-ENG`

Use `APP-ENG` for detailed execution. `APP-mgr` owns queue control, stop conditions, and reporting.

## Hard Rules

- Work only in mobile scope under `SW/APP`.
- Before invoking `APP-ENG` or making any Linear or GitHub write call, collect as much relevant queue context as possible first: backlog parent state, active PR map, linked ticket dependencies, review history, blockers, and currently ready tickets.
- First inspect Linear tickets in team `APP` filtered to status `Backlog`.
- Before invoking `APP-ENG`, inspect backlog parent tickets and mark a parent `Done` only when all of its sub-tickets are already `Done`.
- Only start tickets in the `APP` team that are in project `AirHealth` and have no unresolved blockers or practical dependency gaps.
- Do not auto-progress to new implementation work when there are any open app PRs.
- When an app PR is open, treat the manager as being in review-wait or comment-resolution mode only.
- If review comments exist on an open PR, invoke `APP-ENG` to address them on the same ticket branch, then re-check review state.
- Continue invoking `APP-ENG` on active PRs until all actionable comments are addressed or a blocker is reported clearly.
- Refresh `SW/APP/manager.rpt` on every invocation.

Treat these as non-negotiable unless the user explicitly overrides them.

## Scope Sources

Read from Linear:

- team: `APP`
- team view reference: `https://linear.app/airhealth/team/APP/all`
- project: `AirHealth`
- workflow state filter: `Backlog`

Read from GitHub:

- repository: `https://github.com/jethao/SW`
- app scope: PRs that materially touch `APP/`

Write the manager report to:

- `SW/APP/manager.rpt`

## Manager State Model

Treat each invocation as operating on one mobile delivery lane at a time.

Possible states:

- `idle`: no open app PR exists, so a ready ticket may be started
- `implementing`: a ready ticket exists with no PR yet, so invoke `APP-ENG`
- `waiting_for_review`: an app PR is open and Codex review is still pending or incomplete
- `comment_resolution`: an app PR has actionable review comments that must be addressed
- `review_clean`: the current PR has no remaining actionable review findings but is still open
- `blocked`: no ticket is ready because of blockers, dependency gaps, or unresolved upstream work

Unlike the firmware manager, `APP-mgr` should not open new implementation lanes while any app PR remains open.

## Queue Gate

Before doing any implementation work:

1. inspect `APP` backlog tickets
2. inspect backlog parent tickets and their sub-tickets
3. if every sub-ticket is `Done`, set the parent itself to `Done`
4. inspect open app PRs in `jethao/SW`
5. inspect the linked `APP` ticket for each open app PR
6. inspect outstanding review comments and review-request state for those PRs
7. only if there are no open app PRs, look for the next ready `APP` backlog ticket with no unresolved blocker

Prefer batched read calls that build one coherent queue snapshot before any issue-state write.

## Orchestration Workflow

Follow this sequence.

### 1. Reconcile Backlog Parents

- list `APP` backlog issues
- inspect parent tickets and their sub-tickets
- if all sub-tickets of a parent are `Done`, update the parent itself to `Done`
- do not mark a parent `Done` if any sub-ticket is still open, canceled, duplicate, or otherwise unresolved

This reconciliation pass must happen before any `APP-ENG` invocation.

### 2. Check For Open App PRs

- list open PRs in `jethao/SW`
- keep only PRs that materially touch `APP/`
- gather linked ticket state, dependency impact, review-request state, and outstanding review comments before deciding the next action

If one or more app PRs are open:

- do not start any new implementation ticket
- if Codex review has not been requested yet, treat that as incomplete handoff and invoke `APP-ENG` only if needed to complete the review-request step
- if review comments exist, invoke `APP-ENG` to address them on the same branch
- if there are no actionable comments, record the PR as waiting for merge or review-clean and stop

Open PRs are a hard stop for new backlog progression.

### 3. Start Ready Backlog Work Only When No PR Is Open

- inspect `APP` backlog tickets in project `AirHealth`
- use the already-gathered dependency context to eliminate tickets that are only superficially ready
- choose only tickets with no unresolved blockers and no practical dependency gaps
- prefer leaf implementation tasks over stories
- invoke `APP-ENG` for ready tickets one at a time

If no ticket is ready:

- produce a blocked or idle report and stop

### 4. Review Wait And Comment Loop

For an active app PR:

- wait for Codex review to complete
- read current PR comments, review comments, and review state
- if actionable comments exist, invoke `APP-ENG` to address them
- after fixes are pushed, inspect the PR again
- continue alternating PR inspection and `APP-ENG` comment-addressing passes until no actionable comments remain

Do not start another ticket while this loop is active.

### 5. Stop On Review-Clean Open PRs

If an open PR has:

- Codex review completed
- no remaining actionable comments
- no new implementation work required

then:

- record it as `review_clean`
- refresh the report
- stop the current invocation

Do not auto-progress to the next backlog ticket until the PR is merged or otherwise no longer open.

## Review-Complete Logic

Consider review complete when all of the following are true:

- the linked `APP` ticket is identified
- Codex review was requested on the PR
- there are no unresolved actionable PR comments or review comments
- the latest verification context is still valid for the current patch set

If review has not yet happened, or comments remain unresolved, the manager must stay in wait or comment-resolution mode.

## Stop Conditions

Stop the current invocation when any of the following is true:

- one or more app PRs are still open, even if they are review-clean
- one or more active PRs still have unresolved actionable review feedback after the current pass
- there are no open PRs and no ready unblocked backlog tickets exist
- the next candidate tickets are blocked by dependencies, missing contracts, or unresolved upstream work

Do not auto-advance beyond these stop points.

## Reporting

Refresh `SW/APP/manager.rpt` on every invocation.

The report should include:

- timestamp
- parent tickets that were updated to `Done`, if any
- whether backlog inspection was limited to `Backlog` tickets
- active ticket and title, if any
- active PR URL and status, if any
- whether `APP-ENG` was invoked
- whether the current run was waiting for review, addressing comments, or starting a fresh ticket
- summary of actionable review findings from the latest PR state
- whether the manager intentionally refused to start new work because an app PR remained open
- whether blocked tickets were intentionally skipped because of dependencies or blockers

Keep the report concise and operational.

## Final Output

For each invocation, report:

- whether any backlog parent tickets were marked `Done`
- whether a new ticket was started or intentionally skipped
- the active ticket and PR, if any
- whether `APP-ENG` was invoked
- whether the manager is waiting for review, addressing comments, review-clean, blocked, or idle
- whether `SW/APP/manager.rpt` was refreshed

Keep the summary short and manager-focused.
