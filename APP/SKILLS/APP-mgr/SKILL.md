---
name: app-mgr
description: Manage the AirHealth mobile delivery loop by orchestrating `APP-ENG` and `APP-CR` for the `APP` Linear team and `jethao/SW` app pull requests. Use when Codex should first reconcile backlog parent tickets based on sub-ticket completion, then let `APP-ENG` autonomously implement all currently ready unblocked app tickets in parallel, invoke `APP-CR` to review the resulting open app PRs, alternate between `APP-ENG` and `APP-CR` until comments are addressed, and refresh `SW/APP/manager.rpt` on every run.
---

# App Manager

Use this skill when Codex should act as the mobile work manager for AirHealth rather than as the individual implementer.

The purpose of this skill is to coordinate the mobile delivery loop safely:

1. reconcile backlog parent tickets against their sub-ticket completion state
2. select only ready mobile tickets with no unresolved blockers
3. invoke `APP-ENG` to implement and open PRs for ready tickets
4. wait for PRs to complete, then invoke `APP-CR` to review live PRs against their linked `APP` tickets
5. alternate `APP-ENG` and `APP-CR` until each active PR has no remaining actionable review findings
6. generate a report from the PR review history
7. continue advancing only tickets that still have no unresolved blockers or dependency gaps

## Managed Skills

This skill coordinates:

- `APP-ENG`
- `APP-CR`

Use `APP-ENG` for detailed execution and `APP-CR` for detailed review. `APP-mgr` owns queue control, stop conditions, and reporting.

## Hard Rules

- Work only in mobile scope under `SW/APP`.
- Before invoking `APP-ENG`, `APP-CR`, or making any Linear or GitHub write call, collect as much relevant queue context as possible first: backlog story state, active PR map, linked ticket dependencies, review history, blockers, and currently ready tickets.
- Before invoking `APP-ENG`, inspect backlog parent tickets and mark a parent `Done` only when all of its sub-tickets are already `Done`.
- Only start tickets in the `APP` team that are in project `AirHealth` and have no unresolved blockers or practical dependency gaps.
- Open PRs do not block starting another ticket unless the new ticket depends on one of those still-open tickets or is otherwise practically blocked.
- Do not start a blocked ticket just because some other open PR is review-clean.
- Treat dependency readiness, not merge order alone, as the gate for moving on to the next ticket.
- If GitHub cannot record approval because the same account authored and reviewed the PR, use review history and the latest `APP-CR` pass to determine approval-ready status.
- Generate or refresh a manager report on every invocation.

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

- `idle`: no open APP PR exists, so a ready ticket may be started
- `implementing`: a ready ticket exists with no PR yet, so invoke `APP-ENG`
- `under_review`: a APP PR is open and has unresolved actionable feedback
- `approval_ready`: the current PR has no remaining actionable review findings
- `waiting_for_merge`: the current PR is approval-ready or approved, but still open
- `blocked`: no ticket is ready because of blockers, dependency gaps, or unresolved upstream work

## Queue Gate

Before doing any implementation work:

1. inspect `APP` backlog tickets with the `Story` label
2. for each such story, inspect its sub-tickets
3. if every sub-ticket is `Done`, set the story status to `Done`
4. inspect open APP PRs in `jethao/SW`
5. inspect the linked `APP` ticket for each open APP PR
6. review any active PR that has unresolved actionable feedback
7. also look for the next ready `APP` tickets with no unresolved blocker and no dependency on still-open work

Prefer batched read calls that build one coherent queue snapshot before any issue-state write.

Open PRs are allowed to coexist as long as each newly started ticket is still independently unblocked.

## Orchestration Workflow

Follow this sequence.

### 1. Reconcile Backlog Parents

- list `APP` backlog issues labeled `Story`
- inspect parent tickets and their sub-tickets
- if all sub-tickets of a parent are `Done`, update the parent itself to `Done`
- do not mark a parent `Done` if any sub-ticket is still open, canceled, duplicate, or otherwise unresolved

This reconciliation pass must happen before any `APP-ENG` invocation.

### 2. Check For Open App PRs

- list open PRs in `jethao/SW`
- keep only PRs that materially touch `APP/`
- gather linked ticket state, dependency impact, review-request state, and outstanding review comments before deciding the next action
- if one or more such PRs exist, review the ones with unresolved feedback first
- track which linked tickets are still open so dependent tickets are not started prematurely

If one or more app PRs are open:

- handle review-loop work for any PR with actionable findings
- keep approval-ready but unmerged PRs recorded as open dependency candidates
- continue to queue evaluation for other tickets that remain independently ready

### 3. Start Ready Backlog Work Only When No PR Is Open

- inspect `APP` backlog tickets in project `AirHealth`
- use the already-gathered dependency context to eliminate tickets that are only superficially ready
- choose only tickets with no unresolved blockers and no practical dependency gaps
- prefer leaf implementation tasks over stories
- invoke `APP-ENG` for one or more such ready tickets as appropriate for the current invocation

If no ticket is ready:

- produce a blocked or idle report and stop

### 4. Review Wait And Comment Loop

For active app PRs:

- invoke `APP-CR` when each current patch set has not yet been reviewed or when a fresh review pass is needed
- collect review findings, PR comments, review comments, and current PR status

If `APP-CR` finds actionable issues:

- invoke `APP-ENG` to address those comments on the same ticket branch
- after the fixes land, invoke `APP-CR` again
- continue alternating `APP-ENG` then `APP-CR` until no actionable findings remain

If `APP-CR` finds no actionable issues:

- treat the PR as approval-ready
- keep the PR open in the report as waiting for merge
- continue only with other independently ready tickets

## Review-Complete Logic

Consider review complete when all of the following are true:

- the linked `APP` ticket is identified
- `APP-CR` has reviewed the current patch set
- there are no unresolved actionable PR comments or review comments
- the latest verification context is still valid for the current patch set

If review has not yet happened, or comments remain unresolved, the manager must stay in wait or comment-resolution mode.

## Stop Conditions

Stop the current invocation when any of the following is true:

- one or more active PRs still have unresolved actionable review feedback after the current pass
- all currently open PRs are approval-ready or waiting for merge and there are no additional ready unblocked tickets
- there are no open PRs needing action and no ready unblocked tickets exist
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
- whether the manager intentionally refused to start a new backlog batch because app PRs remained open
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
