# ADR-033: Guarded Writes, Unique Indexes as the Atomic Backstops, and Expiring Samples

## Status
Accepted

## Context
Several instances serve the registry, staff edit policies while tools report samples, and tools retry. Checking first and writing later is not atomic. And a sample every few minutes per policy never stops arriving.

## Decision
- Every change to an existing policy is **one atomic update guarded by the status that was loaded**, which also appends the audit entry. A lost race is 409 (`ERR-CAP-00409`): read again and decide again.
- **A policy name is unique per organisation** (`(organisationId, name)`), on create and on rename; the index is the atomic backstop of a duplicate.
- **One sample per `(organisationId, policyId, measuredAt)`** is a unique index. The loser of a simultaneous insert reads the stored sample and answers it as not created.
- The policy's latest sample is replaced only when the stored one is empty or older, in one guarded update. Order and repetition of reports do not matter.
- **Samples expire.** A TTL index on `measuredAt` removes a sample `thinklab.capacity-registry.sample-retention-days` after it was taken (default 400, so a year-on-year comparison is still possible). Policies and their audit trail do not expire. The trend never reads more than the window (at most 90 days), and at most the newest 1000 samples of it.
- Indexes are created at startup, fail-open and idempotent; `thinklab.mongo.create-indexes=false` turns them off. A second index `(organisationId, policyId, measuredAt desc)` serves the real queries (the newest samples, the trend window).
- The mongo adapters set the update time themselves and never write what the domain did not ask for.

## Consequences
- Positive: with N instances and a retrying tool there is still exactly one sample per moment and no lost updates; the store does not grow without end.
- Negative: without the indexes (they are fail-open) a simultaneous double report could store two samples; the startup log says when an index could not be created. The latest sample and the sample are two writes: a crash between them is repaired by the retry (ADR-030). A TTL monitor in MongoDB runs about once a minute, so an expired sample can outlive its date by that long.
