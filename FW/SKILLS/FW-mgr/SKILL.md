---
name: fw-mgr
description: Manage the AirHealth firmware delivery loop by orchestrating `FW-ENG` and `FW-CR` for the `FIR` Linear team and `jethao/SW` firmware pull requests. Use when Codex should pick only unblocked firmware tickets, invoke `FW-ENG` to implement them, invoke `FW-CR` to review the resulting PRs, alternate between implementation and review until the current PR is approval-ready, generate a review-history report, and then stop without starting the next ticket until the current PR is merged.
---

# Firmware Manager

Use this skill when Codex should act as the firmware work manager for AirHealth rather than as the individual implementer or reviewer.

The purpose of this skill is to coordinate the firmware loop safely:

1. select only ready firmware tickets with no unresolved blockers
2. invoke `FW-ENG` to implement and open the PR for that one ticket
3. invoke `FW-CR` to review the live PR against the linked `FIR` ticket
4. alternate `FW-ENG` and `FW-CR` until the PR has no remaining actionable review findings
5. generate a report from the PR review history
6. stop and wait for merge before starting another ticket

## Managed Skills

This skill coordinates:

- `FW-ENG`
- `FW-CR`

Use those skills for the detailed execution. `FW-mgr` owns queue control, stop conditions, and reporting.

## Hard Rules

- Work only in firmware scope under `SW/FW`.
- Only start tickets in the `FIR` team that are in project `AirHealth` and have no unresolved blockers or practical dependency gaps.
- Do not start a new ticket when there is already an open firmware PR for an unmerged ticket.
- Do not auto-proceed to the next ticket just because the current PR appears approved or review-clean.
- Treat merge as the gate for moving on to the next ticket.
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

Treat each invocation as operating on exactly one active firmware delivery lane.

Possible states:

- `idle`: no open firmware PR exists, so a ready ticket may be started
- `implementing`: a ready ticket exists with no PR yet, so invoke `FW-ENG`
- `under_review`: a firmware PR is open and has unresolved actionable feedback
- `approval_ready`: the current PR has no remaining actionable review findings
- `waiting_for_merge`: the current PR is approval-ready or approved, but still open
- `blocked`: no ticket is ready because of blockers, dependency gaps, or unresolved upstream work

Only one lane should be active at a time.

## Queue Gate

Before doing any implementation work:

1. inspect open firmware PRs in `jethao/SW`
2. inspect the linked `FIR` ticket for each open firmware PR
3. if any relevant PR is still open, work on that PR instead of starting a new ticket
4. only if no active firmware PR exists, look for the next ready `FIR` ticket with no unresolved blocker

Do not start a second ticket while the first ticket still has an open PR.

## Orchestration Workflow

Follow this sequence.

### 1. Check For Active Firmware PRs

- list open PRs in `jethao/SW`
- keep only PRs that materially touch `FW/`
- if one or more such PRs exist, choose the oldest active firmware PR unless the user requested a specific one

If there is an active firmware PR:

- do not start a new ticket
- move directly to review-loop handling

### 2. Start A New Ticket Only When No PR Is Open

If there is no open firmware PR:

- inspect `FIR` backlog tickets in project `AirHealth`
- choose only a ticket with no unresolved blockers or practical dependency gaps
- prefer a leaf implementation task over a story
- invoke `FW-ENG` for that one ticket

If no ticket is ready:

- produce a blocked report and stop

### 3. Review Loop For The Active PR

Once a firmware PR exists:

- invoke `FW-CR`
- collect review findings, PR comments, review comments, and current PR status

If `FW-CR` finds actionable issues:

- invoke `FW-ENG` to address those comments on the same ticket branch
- after the fixes land, invoke `FW-CR` again
- continue alternating `FW-ENG` then `FW-CR` until no actionable findings remain

If `FW-CR` finds no actionable issues:

- treat the PR as approval-ready
- do not start the next ticket
- stop and wait for merge

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

- the active PR still has unresolved actionable review feedback after the current pass
- the active PR is approval-ready and is now waiting for merge
- there is no open PR but no ready unblocked ticket exists
- the current ticket is blocked by a dependency, missing contract, or unresolved upstream work

Do not auto-advance beyond these stop points.

## Reporting

Refresh `SW/FW/fw-mgr.rpt` on every invocation.

The report should include:

- timestamp
- active ticket key and title, if any
- active PR URL and status, if any
- whether the current run invoked `FW-ENG`, `FW-CR`, or both
- summary of actionable review findings from the latest review history
- whether the PR is blocked, under review, approval-ready, or waiting for merge
- whether same-account GitHub review restrictions prevented a formal approval
- whether a new ticket was intentionally not started because an approved or approval-ready PR is still unmerged

Keep the report concise and operational.

## Final Output

For each invocation, report:

- whether a new ticket was started or intentionally not started
- the active ticket and PR, if any
- whether `FW-ENG` was invoked
- whether `FW-CR` was invoked
- current lane status: blocked, under review, approval-ready, or waiting for merge
- whether the manager report was refreshed

Keep the summary short and manager-focused.
