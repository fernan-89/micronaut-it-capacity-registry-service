# ADR-032: The Capacity Registry Is for IT Staff and Their Tools

## Status
Accepted

## Context
Policies name assets and how close they are to running out; samples are measurements of them. A requester (end user) has no business reading them.

## Decision
- Every route refuses a `REQUESTER` with 403 `ERR-CAP-00403`; any other role, or none, is staff (the same rule as the monitor, the alerting service and the incident service). The tools that report samples are service identities, which are not requesters.
- Every lookup is scoped by `X-Tenant-Id`: another tenant's policy or sample is a 404, never a 403.
- **There is no delete.** A policy is paused (its history stays), a sample is a fact.
- Audit is kept on the policy only, for real events (initiated, updated, paused, resumed), with the executor id. Samples are not audited: they are the record themselves, with `reportedBy`.
- **Nothing a tool says is kept as text**: a sample is a number, a moment and the executor.

## Consequences
- Positive: one access rule across the operations services; no free text that could leak a secret.
- Negative: a sample cannot carry a note ("disk replaced"); that stays in the tool or in the policy's audit trail.
