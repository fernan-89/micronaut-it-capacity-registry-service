# micronaut-it-capacity-registry-service

BIAN-aligned Service Domain **it-capacity-registry** (Control Record: `CapacityPolicy`), port `8107`.

The fifth slice of Journey 14 (operations): a **registry of capacity**. A `CapacityPolicy` is the watch on one metric of one asset (disk,
memory, CPU, network, licences...): the limit, the utilisation at which to warn and at which it is critical, how many days of samples the trend
is drawn from, and how far ahead running out is worth a warning. A `CapacitySample` is what a tool reports when it measures. The registry says,
**when you ask**, how full each asset is and, by a straight line through the recent samples, how many days are left.

## What it guarantees, and what it does not

- **It only records** (ADR-030): the tool that measures reports each sample (`sample/initiate`). The registry never measures, schedules or reads
  anything, holds no credential and reaches no asset. The unit is the tool's: the limit and the samples must use the same one.
- **A sample is a fact** (ADR-030): a number and a moment, never edited, never deleted by hand, with no text from the tool at all. A sample may be
  taken at most 5 minutes ahead of the registry's clock. Samples expire after `thinklab.capacity-registry.sample-retention-days` (default 400).
- **Reporting is idempotent** (ADR-030, ADR-033): the same policy and moment is the same sample. A repeat, or 20 at once, answers the stored
  sample with 200 (a new one is 201); the tool may retry as often as it likes.
- **The asset is judged when read** (ADR-031), never stored: `level` is `OK`, `WARNING`, `CRITICAL` (the latest sample against the two
  percentages, with hysteresis: a level is entered at its percentage and left only 5 points below it, ADR-034), `NO_DATA` or `PAUSED`
  (not judged); `stale` says the tool has been silent for more than twice its expected interval.
- **A simple forecast** (ADR-031): a least-squares line through the samples of the window gives `forecast` `STABLE` (flat or falling),
  `WITHIN_HORIZON` or `BEYOND_HORIZON`, with `daysToLimit` and `projectedExhaustionAt` counted from the latest sample, or
  `NOT_ENOUGH_DATA` (fewer than two samples, or less than an hour between them). No model beyond the line: a saw-tooth disk will be mis-read.
- **A policy cannot promise nonsense**: the critical percentage is above the warning one, the limit is positive.
- **Staff only** (ADR-032): every route refuses a `REQUESTER` with 403 `ERR-CAP-00403`; another tenant's policy or sample answers 404.
  There is no delete (a policy is paused).
- **Safe with several instances** (ADR-033): a policy name is unique per organisation, one sample per `(policy, moment)` is a unique index,
  every change is guarded by the status loaded, and the latest sample only moves forward in time whatever the order of the reports.
- **Not here yet:** telling someone when an asset is about to run out (a later pairing with alerting), seasonality, collecting the samples
  (the tools do), per-unit conversion.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call; `X-Executor` is mandatory on the actions (for a sample, the name of the tool); `X-Role` is optional
and, with platform security on, comes from the verified token.

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-capacity-registry/v1/initiate` `{"name":"Orders disk","assetId":"<uuid>","metric":"DISK","limit":500,"warnPercent":70,"criticalPercent":90,"windowDays":30,"horizonDays":60,"reportEveryHours":24}` (`metric`: DISK, MEMORY, CPU, NETWORK, LICENSES, OTHER) |
| retrieve | `GET /it-capacity-registry/v1/{id}/retrieve` (judged now) |
| promises/retrieve | `GET /it-capacity-registry/v1/promises/retrieve?changedWithinDays=&limit=` (the promises contract, ADR-035: every broken promise and every one that changed lately, each with `verdict`, a fixed `code` when broken, `status` and `changedAt`) |
| retrieve (collection) | `GET /it-capacity-registry/v1/retrieve?status=&assetId=&metric=&level=&forecast=` (oldest first) |
| update | `PUT /it-capacity-registry/v1/{id}/update` (the whole definition again, in any status) |
| control/pause | `PUT /it-capacity-registry/v1/{id}/control/pause` (ACTIVE -> PAUSED: no longer judged) |
| control/resume | `PUT /it-capacity-registry/v1/{id}/control/resume` (PAUSED -> ACTIVE) |
| audit-log/retrieve | `GET /it-capacity-registry/v1/{id}/audit-log/retrieve` |
| sample/initiate | `POST /it-capacity-registry/v1/sample/initiate` `{"policyId":"<uuid>","value":312.5,"measuredAt":"<instant>"}` |
| sample/retrieve | `GET /it-capacity-registry/v1/sample/{id}/retrieve` |
| sample/retrieve (collection) | `GET /it-capacity-registry/v1/sample/retrieve?policyId=&limit=` (newest first, 50 unless asked, at most 200) |

```text
policy: ACTIVE <-> PAUSED
sample: a fact (a value at a moment), never changes, expires
```

```bash
curl -X POST "http://localhost:8107/it-capacity-registry/v1/sample/initiate" -H "X-Tenant-Id: <organisationId>" -H "X-Executor: disk-agent-1" \
  -H "Content-Type: application/json" \
  -d '{"policyId":"<policyId>","value":312.5,"measuredAt":"2026-10-18T02:00:00Z"}'
```

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-CAP-00403` | 403 | A REQUESTER used the capacity registry (ADR-032) |
| `ERR-CAP-00404` | 404 | Policy or sample not found (another tenant's answers the same) |
| `ERR-CAP-00409` | 409 | Duplicate policy name, illegal transition (pause a paused policy), or it changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure, a critical percentage not above the warning one, a negative or non-numeric value, a sample from the future |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Configuration

`HASH_SERVICE_URL` (default `http://localhost:8080`, the sovereign ids), `MICRONAUT_SERVER_PORT` (default 8107), `mongodb.uri`,
`thinklab.mongo.create-indexes` (default on), `THINKLAB_CAPACITY_SAMPLE_RETENTION_DAYS` (default 400). The service calls no other service and
has no scheduler.

Do not put personal data in a policy name: it is stored with the policy.

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 the tool reports, the registry keeps the samples · 031 the asset is judged when read, with a simple linear forecast · 032 staff only ·
033 guarded writes, unique backstops and expiring samples · 035 the promises contract.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
