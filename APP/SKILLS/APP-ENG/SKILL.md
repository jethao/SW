---
name: app-eng
description: Execute AirHealth mobile app work from Linear by reading backlog issues in the `APP` team for the AirHealth project, checking dependency readiness, implementing only unblocked tickets, keeping all app code in `SW/APP`, adding tests for the implemented feature when they do not already exist, creating one linked pull request per completed ticket, creating each ticket branch from `main`, and requesting Codex review on the GitHub PR before considering the ticket handed off. Use when Codex needs to act as the mobile implementation engineer with autonomy to process multiple ready, unblocked tickets without waiting for a manager between tickets.
---

# App Engineer

Use this skill when Codex should implement one or more ready mobile app tickets from Linear for AirHealth.

The job of this skill is to pick only mobile tickets that are truly ready, implement them in the app tree, and land each completed ticket as its own pull request linked back to the original Linear issue.

## Hard Rules

- All mobile app code must live under `SW/APP`.
- Before making any Linear or GitHub API write call, gather as much relevant context as possible first: ticket details, parent and child relationships, blockers, open PR state, review comments, and the relevant local app code and tests.
- Do not start a ticket that has any unresolved blocker.
- APP-ENG has autonomy to work through multiple tickets in the same invocation when each ticket is independently ready and unblocked.
- Do not bundle multiple Linear tickets into one implementation PR.
- Create exactly one PR per implemented ticket.
- Keep each ticket isolated in its own branch, commit set, PR, and final summary entry.
- Create each ticket branch from the latest `main` branch baseline.
- Link each PR to the original Linear ticket.
- If tests for the implemented feature do not already exist, add them as part of the same ticket when the feature is testable.
- After creating the PR, request Codex review on the GitHub PR before considering the ticket handed off.
- Do not implement epic-only or story-only umbrella tickets when there are child task tickets that should be delivered first.

Treat these as non-negotiable unless the user explicitly overrides them.

## Ticket Source

Read tickets from Linear:

- team: `APP`
- team view reference: `https://linear.app/airhealth/team/APP/all`
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
- the work would require code outside `SW/APP` unless the user explicitly approves an exception

When a ticket is blocked, stop on that ticket and move on to another ready ticket instead of partially implementing it.

## Source Tree Rule

Keep all app code under:

- `SW/APP`

This includes:

- application code
- UI code
- reducers, stores, hooks, and navigation glue
- feature tests
- build or config files that belong to the app delivery tree

Non-code artifacts may remain outside `SW/APP` when appropriate, such as:

- skills under `SW/APP/SKILLS`
- repo-level documentation
- product or architecture documents under `PM/` or `SW/Architecture`

If the current repo layout does not yet support the ticket cleanly, extend `SW/APP` rather than creating a new code root elsewhere.

## Recommended Workflow

Follow this sequence.

### 1. Build The Ready Queue

- Read backlog tickets in `APP` for project `AirHealth`.
- Collect parent and dependency context.
- Inspect existing open app PRs, linked ticket state, and any outstanding PR comments before deciding whether to start or update work.
- Filter to tickets with no unresolved blockers.
- Prefer tickets that are already implementation-sized.
- If multiple tickets are ready, APP-ENG should autonomously continue through all of the currently ready unblocked tickets it can safely complete in the same invocation.
- Default to sequential processing. Only work on multiple tickets in parallel when their write scopes are clearly disjoint and each ticket still gets its own branch and PR.
- Do not wait for a manager confirmation between ready tickets unless a new blocker, ambiguous scope boundary, or tooling risk appears.

### 2. Confirm The Implementation Boundary

Before editing code:

- read the ticket description, acceptance criteria, and dependencies
- read parent, sibling, sub-ticket, and blocker context as needed to avoid missing scope or sequencing information
- inspect relevant app code under `SW/APP`
- inspect existing tests, build glue, and any open PR comments or review feedback relevant to the same ticket
- read PRD, architecture, or mobile feature-design documents only when needed to resolve ambiguity
- keep the planned change small enough for one reviewable PR

If a ticket is too large, do not silently implement part of it. Stop and report that the ticket should be split.

### 3. Implement Only Ready Tickets

For each ready ticket you decide to process:

- make the smallest complete change that satisfies that ticket
- keep edits focused on that ticket only
- check whether tests already exist for the implemented feature
- add tests when they do not exist yet and the feature is testable
- update existing tests when the feature or behavior changes
- keep code, tests, and build glue inside `SW/APP`

Before starting the next ticket in the same invocation:

- finish the current ticket's verification, branch, commit, PR, and Codex review request
- return the worktree to a clean baseline for the next ticket
- do not carry unfinished mixed changes from one ticket into another
- continue directly into the next ready unblocked ticket if one is available

### 4. Verify

Run the most relevant verification available for the changed area, such as:

- unit or integration tests
- app-targeted build checks
- UI, reducer, or route tests
- platform-specific checks when available

If verification cannot run, say so clearly in the final note and PR description.

Lack of existing tests is not a reason to skip them. If the feature is testable, add the missing tests in the same PR.

### 5. Create The PR

After the ticket is implemented:

- refresh `main` or `origin/main` and create the ticket branch from that baseline
- commit only the ticket's changes
- open a PR dedicated to that ticket
- include the Linear ticket key in the branch name, commit message, PR title, and PR body when possible

Recommended patterns:

- branch: `codex/app-123-short-slug`
- commit title: `APP-123 Implement <short summary>`
- PR title: `APP-123 Implement <short summary>`

In the PR body:

- summarize the implementation
- summarize verification
- include the Linear issue URL
- include explicit wording linking the PR to the Linear ticket

If the GitHub and Linear integration is available, attach the PR to the ticket directly. Otherwise, ensure the PR title and body clearly reference the ticket so the integration can link it automatically.

### 6. Request Codex Review

After the PR is open:

- request Codex review on the GitHub PR
- gather enough PR context first so the review request is attached to the correct PR
- do not consider the ticket handed off until the review request has been made or a tooling blocker has been reported clearly

Preferred behavior:

- use the available GitHub review-request mechanism to request review from Codex on the PR
- if a formal review request is unavailable, leave an explicit PR comment asking Codex to review the PR and report that fallback in the final summary

### 7. Update The Ticket

When Linear write access is available:

- make the write only after the implementation, verification, PR context, and Codex review request are fully assembled
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

It is fine to process multiple ready tickets in one run as long as each ticket remains isolated and unblocked.
By default, do that sequentially, one ticket after another.
Do not start multiple tickets in parallel in the same codebase slice unless the user explicitly asks for parallel work.

## Stop Conditions

Do not implement and do not create a PR when:

- the ticket has unresolved blockers
- the ticket depends on missing upstream code or contracts
- the ticket is too broad for one PR
- the implementation would need code outside `SW/APP` and the user has not approved that exception

In these cases, give a short explanation of why the ticket is not ready and identify the blocker.
If one ticket is blocked, skip it and continue to any other ready unblocked ticket instead of ending the whole run unnecessarily.
Stop only when there are no more ready unblocked tickets, an open-PR policy from the invoking workflow prevents further starts, or a real blocker/risk needs escalation.

## Final Output

For each processed ticket, report:

- ticket key and title
- whether it was implemented or skipped
- why it was skipped, if blocked
- key files changed under `SW/APP`
- verification performed
- PR URL
- whether Codex review was requested on the PR, or why that step could not be completed

Keep the summary concise and execution-focused.
