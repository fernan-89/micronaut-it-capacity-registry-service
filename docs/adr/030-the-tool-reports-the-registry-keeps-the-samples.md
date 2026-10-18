# ADR-030: The Tool Reports, the Registry Keeps the Samples

## Status
Accepted

## Context
Capacity is measured by tools (a monitoring agent, a cloud API, a script) that the registry does not control. The registry has to say how full an asset is and where it is heading, without ever holding a credential, reaching an asset or measuring anything.

## Decision
- **The registry only records.** A `CapacityPolicy` is the watch on one asset and one metric: the `limit` (in whatever unit the tool reports in), the utilisation at which to warn and at which it is critical, the trend window and forecast horizon in days, and how often the tool is expected to report. A `CapacitySample` is what a tool reports when it measures (`sample/initiate`): a value and the moment. Nothing here measures, schedules or reads anything.
- **A sample is a fact**: immutable, never edited, never deleted by hand. It is a number and a moment; there is no text from the tool at all, so nothing can carry a path or a credential. Samples **expire** (ADR-033).
- **Reporting is idempotent**: the same policy and moment is the same sample (unique index on `(organisationId, policyId, measuredAt)`, times cut to the millisecond). A repeat, or twenty at once, answers the stored sample with 200 instead of 201, and no sovereign id is spent for it. A different value for the same moment does not overwrite the first.
- **The clock of the tool may be a little off**: a sample may be taken at most 5 minutes after the registry's clock, never in the future beyond that. A value is a finite number of zero or more.
- **The policy keeps the latest sample** (value and moment) so that its level can be judged without reading any sample. It only moves forward in time (a compare on the moment), so late or repeated reports change nothing they should not. A report that stored the sample and died before that update is repaired by the tool's retry, because the latest sample is folded again even for a sample already stored.

## Consequences
- Positive: no credentials, no access to assets, nothing to secure on the target side; any tool that can call an HTTP route can report.
- Negative: the registry trusts what the tool says. A tool that stops reporting is seen as stale (ADR-031), which is the point, but a tool that lies cannot be caught here. The unit is the tool's: a limit in gigabytes and samples in bytes would be judged wrongly, and the policy cannot know.
