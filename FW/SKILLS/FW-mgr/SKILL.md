---
name: fw-mgr
description: Manage the AirHealth firmware delivery loop by orchestrating `FW-ENG` and `FW-CR` for the `FIR` Linear team and `jethao/SW` firmware pull requests. Use when Codex should first reconcile backlog story tickets based on sub-ticket completion, then pick any unblocked firmware tickets, invoke `FW-ENG` to implement them, invoke `FW-CR` to review resulting PRs, alternate between implementation and review as needed, generate a review-history report, and continue advancing only tickets that still have no unresolved blockers or dependency gaps.
---

# Firmware Manager

Use this skill when Codex should act as the firmware work manager for AirHealth rather than as the individual implementer or reviewer.

The purpose of this skill is to coordinate the firmware loop safely:

1. reconcile backlog story tickets against their sub-ticket completion state
2. select only ready firmware tickets with no unresolved blockers
3. invoke `FW-ENG` to implement and open PRs for ready tickets
4. wait for PRs to complete, then invoke `FW-CR` to review live PRs against their linked `FIR` tickets
5. alternate `FW-ENG` and `FW-CR` until each active PR has no remaining actionable review findings
6. generate a report from the PR review history
7. continue advancing only tickets that still have no unresolved blockers or dependency gaps

## Managed Skills

This skill coordinates:

- `FW-ENG`
- `FW-CR`

Use those skills for the detailed execution. `FW-mgr` owns queue control, stop conditions, and reporting.

## Hard Rules

- Work only in firmware scope under `SW/FW`.
- Before invoking `FW-ENG`, `FW-CR`, or making any Linear or GitHub write call, collect as much relevant queue context as possible first: backlog story state, active PR map, linked ticket dependencies, review history, blockers, and currently ready tickets.
- Before invoking `FW-ENG`, inspect backlog issues in `FIR` with the `Story` label and mark a story `Done` when all of its sub-tickets are already `Done`.
- Only start tickets in the `FIR` team that are in project `AirHealth` and have no unresolved blockers or practical dependency gaps.
- Open PRs do not block starting another ticket unless the new ticket depends on one of those still-open tickets or is otherwise practically blocked.
- Do not start a blocked ticket just because some other open PR is review-clean.
- Treat dependency readiness, not merge order alone, as the gate for moving on to the next ticket.
- If GitHub cannot record approval because the same account authored and reviewed the PR, use review history and the latest `FW-CR` pass to determine approval-ready status.
- Generate or refresh a manager report on every invocation.

Treat these as non-negotiable unless the user explicitly overrides them.

## Scope Sources

Read from Linear:

- team: `FIR`
- team view reference: `https://linear.app/airhealth/team/FIR/all`
- project: `AirHealth`

Read from GitHub:

- repository: `https://github.com/jethao/SW`
- firmware scope: PRs that materially touch `FW/`

Write the manager report to:

- `SW/FW/fw-mgr.rpt`

## Manager State Model

Treat each invocation as operating on one or more active firmware delivery lanes.

Possible states for each lane:

- `idle`: no open firmware PR exists, so a ready ticket may be started
- `implementing`: a ready ticket exists with no PR yet, so invoke `FW-ENG`
- `under_review`: a firmware PR is open and has unresolved actionable feedback
- `approval_ready`: the current PR has no remaining actionable review findings
- `waiting_for_merge`: the current PR is approval-ready or approved, but still open
- `blocked`: no ticket is ready because of blockers, dependency gaps, or unresolved upstream work

Multiple lanes may be active at the same time, but only for tickets that are independently ready.

## Queue Gate

Before doing any implementation work:

1. inspect backlog issues in `FIR` with the `Story` label
2. for each such story, inspect its sub-tickets
3. if every sub-ticket is `Done`, set the story status to `Done`
4. inspect open firmware PRs in `jethao/SW`
5. inspect the linked `FIR` ticket for each open firmware PR
6. review any active PR that has unresolved actionable feedback
7. also look for the next ready `FIR` tickets with no unresolved blocker and no dependency on still-open work

Prefer batched read calls that build one coherent queue snapshot before any issue-state or PR-state write.

Open PRs are allowed to coexist as long as each newly started ticket is still independently unblocked.

## Orchestration Workflow

Follow this sequence.

### 1. Reconcile Backlog Stories

- list `FIR` backlog issues labeled `Story`
- inspect sub-tickets for each story
- if all sub-tickets are `Done`, update the story itself to `Done`
- do not mark a story `Done` if any sub-ticket is still open, canceled, duplicate, or otherwise unresolved

This story-reconciliation pass must happen before any `FW-ENG` invocation.

### 2. Check For Active Firmware PRs

- list open PRs in `jethao/SW`
- keep only PRs that materially touch `FW/`
- gather linked ticket state, dependency impact, and outstanding review comments for those PRs before deciding the next action
- if one or more such PRs exist, review the ones with unresolved feedback first
- track which linked tickets are still open so dependent tickets are not started prematurely

If there are active firmware PRs:

- handle review-loop work for any PR with actionable findings
- keep approval-ready but unmerged PRs recorded as open dependency candidates
- continue to queue evaluation for other tickets that remain independently ready

### 3. Start Ready Tickets Even When Other PRs Are Open

- inspect `FIR` backlog tickets in project `AirHealth`
- use the already-gathered dependency and PR context to eliminate tickets that are only superficially ready
- choose only tickets with no unresolved blockers, no practical dependency gaps, and no dependency on still-open PR work
- prefer leaf implementation tasks over stories
- invoke `FW-ENG` for one or more such ready tickets as appropriate for the current invocation

If no additional ticket is ready:

- produce a blocked or waiting report and stop

### 4. Review Loop For Active PRs

For each active firmware PR that needs review attention:

- invoke `FW-CR`
- collect review findings, PR comments, review comments, and current PR status

If `FW-CR` finds actionable issues:

- invoke `FW-ENG` to address those comments on the same ticket branch
- after the fixes land, invoke `FW-CR` again
- continue alternating `FW-ENG` then `FW-CR` until no actionable findings remain

If `FW-CR` finds no actionable issues:

- treat the PR as approval-ready
- keep the PR open in the report as waiting for merge
- continue only with other independently ready tickets

## Approval-Ready Logic

Consider a PR approval-ready when all of the following are true:

- the linked `FIR` ticket is identified
- the latest `FW-CR` pass has no findings
- there are no unresolved actionable PR comments or review comments
- the most relevant verification for the ticket has passed or the limitation is clearly documented

GitHub approval status alone is not required when approval is blocked by same-account restrictions.

If GitHub refuses the formal approval action because the reviewer is also the author, record that in the report and treat the PR as approval-ready if the substantive review is clean.

## Stop Conditions

Stop the current invocation when any of the following is true:

- one or more active PRs still have unresolved actionable review feedback after the current pass
- all currently open PRs are approval-ready or waiting for merge and there are no additional ready unblocked tickets
- there are no open PRs needing action and no ready unblocked tickets exist
- the next candidate tickets are blocked by dependencies, missing contracts, or unresolved upstream work

Do not auto-advance beyond these stop points.

## Reporting

Refresh `SW/FW/fw-mgr.rpt` on every invocation.

The report should include:

- timestamp
- stories that were updated to `Done`, if any
- active tickets and titles, if any
- active PR URLs and statuses, if any
- whether the current run invoked `FW-ENG`, `FW-CR`, or both
- summary of actionable review findings from the latest review history
- whether each active lane is blocked, under review, approval-ready, or waiting for merge
- whether same-account GitHub review restrictions prevented a formal approval
- whether additional ready tickets were started while unrelated PRs remained open
- whether blocked tickets were intentionally skipped because of dependencies or blockers

Keep the report concise and operational.

## Final Output

For each invocation, report:

- whether any backlog stories were marked `Done`
- whether new tickets were started or intentionally skipped
- the active tickets and PRs, if any
- whether `FW-ENG` was invoked
- whether `FW-CR` was invoked
- current lane status for each active lane: blocked, under review, approval-ready, or waiting for merge
- whether the manager report was refreshed

Keep the summary short and manager-focused.
