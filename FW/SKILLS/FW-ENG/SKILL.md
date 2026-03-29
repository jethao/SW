---
name: fw-eng
description: Execute AirHealth firmware work from Linear by reading backlog issues in the `FIR` team for the AirHealth project, checking dependency readiness, implementing only unblocked tickets, keeping all firmware code in `SW/FW/Source`, adding unit tests for the implemented feature when they do not already exist, creating one linked pull request per completed ticket, and creating each ticket branch from `main`. Use when Codex needs to act as the firmware implementation engineer for ready Linear tickets without starting blocked work.
---

# Firmware Engineer

Use this skill when Codex should implement ready firmware tickets from Linear for AirHealth.

The job of this skill is to pick only firmware tickets that are truly ready, implement them in the firmware source tree, and land each completed ticket as its own pull request linked back to the original Linear issue.

## Hard Rules

- All firmware code must live under `SW/FW/Source`.
- Do not start a ticket that has any unresolved blocker.
- Do not bundle multiple Linear tickets into one implementation PR.
- Create exactly one PR per implemented ticket.
- Create each ticket branch from the latest `main` branch baseline.
- Link each PR to the original Linear ticket.
- If unit tests for the implemented feature do not already exist, add them as part of the same ticket.
- Do not implement epic-only or story-only umbrella tickets when there are child task tickets that should be delivered first.

Treat these as non-negotiable unless the user explicitly overrides them.

## Ticket Source

Read tickets from Linear:

- team: `FIR`
- team view reference: `https://linear.app/airhealth/team/FIR/all`
- project: `AirHealth`
- workflow state: `Backlog`

Prefer task-level issues that are leaf work items. Only implement a story directly when it has no child task tickets and is still small enough for a reviewable PR.

## Dependency Gate

Before implementing any ticket:

1. Read the candidate ticket.
2. Read its parent, sub-issues, and dependency relationships when available.
3. Determine whether it has unresolved blockers.
4. Determine whether it depends on unfinished sibling or parent work in practice, even if the dependency link is missing.

Proceed only if all of the following are true:

- the ticket is in the `AirHealth` project
- the ticket is in `Backlog`
- the ticket is a leaf implementation ticket or a self-contained story
- there are no unresolved blockers
- the ticket is specific enough to implement safely

Do not proceed if:

- a blocker is still open
- the ticket is only an umbrella issue
- acceptance criteria are too vague to implement safely
- the work would require code outside `SW/FW/Source` unless the user explicitly approves an exception

When a ticket is blocked, stop on that ticket and move on to another ready ticket instead of partially implementing it.

## Source Tree Rule

Keep all firmware code under:

- `SW/FW/Source`

This includes:

- application code
- platform glue
- protocol handlers
- state machines
- tests that are part of firmware source delivery
- build files that belong to the firmware source tree

Non-code artifacts may remain outside `SW/FW/Source` when appropriate, such as:

- skills under `SW/FW/SKILLS`
- reports such as `SW/FW/initialize.rpt`
- repo-level documentation

If the current repo layout does not yet support the ticket cleanly, extend `SW/FW/Source` rather than creating new code roots elsewhere.

## Recommended Workflow

Follow this sequence.

### 1. Build The Ready Queue

- Read backlog tickets in `FIR` for project `AirHealth`.
- Collect parent and dependency context.
- Filter to tickets with no unresolved blockers.
- Prefer tickets that are already implementation-sized.
- If multiple tickets are ready, process them one at a time.

### 2. Confirm The Implementation Boundary

Before editing code:

- read the ticket description, acceptance criteria, and dependencies
- inspect relevant firmware code under `SW/FW/Source`
- read PRD, architecture, or feature-design documents only when needed to resolve ambiguity
- keep the planned change small enough for one reviewable PR

If a ticket is too large, do not silently implement part of it. Stop and report that the ticket should be split.

### 3. Implement Only The Ready Ticket

- make the smallest complete change that satisfies the ticket
- keep edits focused on one ticket only
- check whether unit tests already exist for the implemented feature
- add unit tests when they do not exist yet
- update existing tests when the feature or behavior changes
- keep code, tests, and build glue inside `SW/FW/Source`

### 4. Verify

Run the most relevant verification available for the changed area, such as:

- unit tests
- firmware-targeted build checks
- protocol tests
- bench or harness commands when available

If verification cannot run, say so clearly in the final note and PR description.

Lack of existing unit tests is not a reason to skip them. If the feature is testable at the unit level, add the missing tests in the same PR.

### 5. Create The PR

After the ticket is implemented:

- refresh `main` or `origin/main` and create the ticket branch from that baseline
- commit only the ticket’s changes
- open a PR dedicated to that ticket
- include the Linear ticket key in the branch name, commit message, PR title, and PR body when possible

Recommended patterns:

- branch: `codex/fir-123-short-slug`
- commit title: `FIR-123 Implement <short summary>`
- PR title: `FIR-123 Implement <short summary>`

In the PR body:

- summarize the implementation
- summarize verification
- include the Linear issue URL
- include explicit wording linking the PR to the Linear ticket

If the GitHub and Linear integration is available, attach the PR to the ticket directly. Otherwise, ensure the PR title and body clearly reference the ticket so the integration can link it automatically.

### 6. Update The Ticket

When Linear write access is available:

- add the PR link to the issue
- move the issue out of `Backlog` only if the user asked for state changes or the workflow clearly expects it

When Linear write access is not available:

- report the PR URL and the intended ticket link in the final response

## Selection Priorities

When more than one ticket is ready, prefer this order:

1. explicitly requested ticket
2. leaf task with no blockers
3. smallest self-contained ticket
4. foundational ticket that unblocks other ready backlog work
5. oldest ready ticket when the above are equal

Avoid starting multiple tickets in parallel in the same codebase slice unless the user explicitly asks for that.

## Stop Conditions

Do not implement and do not create a PR when:

- the ticket has unresolved blockers
- the ticket depends on missing upstream code or contracts
- the ticket is too broad for one PR
- the implementation would need code outside `SW/FW/Source` and the user has not approved that exception

In these cases, give a short explanation of why the ticket is not ready and identify the blocker.

## Final Output

For each processed ticket, report:

- ticket key and title
- whether it was implemented or skipped
- why it was skipped, if blocked
- key files changed under `SW/FW/Source`
- verification performed
- PR URL, or why PR creation could not be completed

Keep the summary concise and execution-focused.
