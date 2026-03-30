---
name: app-mgr
description: Manage the AirHealth mobile delivery loop by orchestrating `APP-ENG` and `APP-CR` for the `APP` Linear team and `jethao/SW` app pull requests. Use when Codex should first reconcile backlog parent tickets based on sub-ticket completion, then let `APP-ENG` autonomously implement all currently ready unblocked app tickets, stop starting new work whenever app PRs are already open, invoke `APP-CR` to review open app PRs, alternate between `APP-ENG` and `APP-CR` until comments are addressed, and refresh `SW/APP/manager.rpt` on every run.
---

# App Manager

Use this skill when Codex should act as the mobile work manager for AirHealth rather than as the individual implementer.

The purpose of this skill is to coordinate the mobile delivery loop safely:

1. reconcile backlog parent tickets against their sub-ticket completion state
2. select only ready mobile tickets with no unresolved blockers
3. invoke `APP-ENG` to implement ready tickets and open PRs
4. stop starting new work while app PRs are open
5. invoke `APP-CR` to review open app PRs
6. invoke `APP-ENG` to address actionable review comments when needed
7. continue alternating `APP-CR` and `APP-ENG` until review is clean or blocked
8. refresh the manager report with the current state

## Managed Skills

This skill coordinates:

- `APP-ENG`
- `APP-CR`

Use `APP-ENG` for detailed execution and `APP-CR` for detailed review. `APP-mgr` owns queue control, stop conditions, and reporting.

## Hard Rules

- Work only in mobile scope under `SW/APP`.
- Before invoking `APP-ENG`, `APP-CR`, or making any Linear or GitHub write call, collect as much relevant queue context as possible first: backlog parent state, active PR map, linked ticket dependencies, review history, blockers, and currently ready tickets.
- First inspect Linear tickets in team `APP` filtered to status `Backlog`.
- Before invoking `APP-ENG`, inspect backlog parent tickets and mark a parent `Done` only when all of its sub-tickets are already `Done`.
- Only start tickets in the `APP` team that are in project `AirHealth` and have no unresolved blockers or practical dependency gaps.
- Do not auto-progress to new implementation work when there are any open app PRs.
- If no app PRs are open at the start of the implementation phase, APP-mgr may hand control to `APP-ENG` and let it process multiple ready unblocked tickets in that same invocation.
- When an app PR is open, treat the manager as being in review-wait or comment-resolution mode only.
- After `APP-ENG` opens or updates an app PR, invoke `APP-CR` to review the current patch set before deciding the next step.
- If `APP-CR` or GitHub review feedback leaves actionable comments on an open PR, invoke `APP-ENG` to address them on the same ticket branch, then re-check review state.
- Continue alternating `APP-CR` and `APP-ENG` on active PRs until review is approved, review-clean, or a blocker is reported clearly.
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

Treat each invocation as managing the current mobile delivery queue, which may yield one or more newly opened implementation lanes from a single `APP-ENG` pass when no PRs were open at the start.

Possible states:

- `idle`: no open app PR exists, so ready ticket work may be started
- `implementing`: one or more ready tickets exist with no open app PR yet, so invoke `APP-ENG`
- `waiting_for_review`: an app PR is open and `APP-CR` review is still pending or incomplete
- `comment_resolution`: an app PR has actionable review comments that must be addressed
- `review_clean`: the current PR has no remaining actionable review findings from `APP-CR` but is still open
- `blocked`: no ticket is ready because of blockers, dependency gaps, or unresolved upstream work

Unlike the firmware manager, `APP-mgr` should not start another implementation pass while any app PR remains open.

## Queue Gate

Before doing any implementation work:

1. inspect `APP` backlog tickets
2. inspect backlog parent tickets and their sub-tickets
3. if every sub-ticket is `Done`, set the parent itself to `Done`
4. inspect open app PRs in `jethao/SW`
5. inspect the linked `APP` ticket for each open app PR
6. inspect outstanding review comments and review state for those PRs
7. only if there are no open app PRs, look for the current set of ready `APP` backlog tickets with no unresolved blocker

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
- if the current patch set has not yet been reviewed, invoke `APP-CR`
- if review comments exist, invoke `APP-ENG` to address them on the same branch
- after fixes are pushed, invoke `APP-CR` again on the updated patch set
- if there are no actionable comments, record the PR as waiting for merge or review-clean and stop

Open PRs are a hard stop for new backlog progression.

### 3. Start Ready Backlog Work Only When No PR Is Open

- inspect `APP` backlog tickets in project `AirHealth`
- use the already-gathered dependency context to eliminate tickets that are only superficially ready
- choose only tickets with no unresolved blockers and no practical dependency gaps
- prefer leaf implementation tasks over stories
- invoke `APP-ENG` once and allow it to work through the currently ready unblocked tickets autonomously
- expect `APP-ENG` to isolate each ticket in its own branch and PR even when it processes multiple tickets in one invocation

If no ticket is ready:

- produce a blocked or idle report and stop

### 4. Review Wait And Comment Loop

For an active app PR:

- invoke `APP-CR` when the current patch set has not yet been reviewed or when a fresh review pass is needed
- read current PR comments, review comments, and review state
- if actionable comments exist, invoke `APP-ENG` to address them
- after fixes are pushed, invoke `APP-CR` again on the updated patch set
- continue alternating `APP-CR` and `APP-ENG` until no actionable comments remain

Do not start another implementation pass while this loop is active.

### 5. Stop On Review-Clean Open PRs

If an open PR has:

- `APP-CR` review completed
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
- `APP-CR` has reviewed the current patch set
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
- when applicable, multiple active tickets or PRs opened by the same `APP-ENG` pass
- whether `APP-ENG` was invoked
- whether `APP-CR` was invoked
- whether the current run was waiting for review, addressing comments, or starting a fresh ticket
- summary of actionable review findings from the latest PR state
- whether the manager intentionally refused to start new work because an app PR remained open
- whether blocked tickets were intentionally skipped because of dependencies or blockers

Keep the report concise and operational.

## Final Output

For each invocation, report:

- whether any backlog parent tickets were marked `Done`
- whether new ticket work was started or intentionally skipped
- the active ticket or tickets and PR or PRs, if any
- whether `APP-ENG` was invoked
- whether `APP-CR` was invoked
- whether the manager is waiting for review, addressing comments, review-clean, blocked, or idle
- whether `SW/APP/manager.rpt` was refreshed

Keep the summary short and manager-focused.
