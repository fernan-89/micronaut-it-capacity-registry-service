# ADR-031: The Asset Is Judged When Read, With a Simple Linear Forecast

## Status
Accepted

## Context
"How full is it and when will it run out" changes with time and with the next sample. Storing the verdict means a job that keeps it fresh, and a stale verdict is worse than none.

## Decision
- **Nothing of the judgment is stored.** Every read of a policy (`retrieve` and the list) judges it at that moment from the policy and the samples of its trend window.
- **Level.** The latest sample divided by the limit is the utilisation. `CRITICAL` from the critical percentage, `WARNING` from the warning one (each starting at its own value), else `OK`. `NO_DATA` before the first sample. `PAUSED` is not judged at all.
- **Stale.** A sample is stale when the tool has been silent for more than twice its expected reporting interval. It is a flag next to the level, not a level: a full disk stays critical even when nobody reports.
- **Forecast, simple on purpose.** A least-squares straight line through the samples of the last `windowDays` days gives a slope in units per day. `NOT_ENOUGH_DATA` with fewer than two samples or less than an hour between the first and the last; `STABLE` when the slope is flat or falling; otherwise `daysToLimit` is `(limit - latest value) / slope` from the **latest sample** (not the fitted line, which is what a person would do with a ruler), `0` when it is already over, and `projectedExhaustionAt` is that many days after the latest sample. `WITHIN_HORIZON` when `daysToLimit` is at most `horizonDays`, else `BEYOND_HORIZON`.
- **No model.** No seasonality, no weekly cycles, no outlier removal beyond the fit itself. A saw-tooth disk (grows, then is cleaned) will be mis-read; the answer is a shorter window or no horizon, not a cleverer model, until a real need says otherwise.
- **A policy cannot promise nonsense**: the critical percentage is above the warning one (up to 100), the limit is positive, window 1 to 90 days, horizon 1 to 365, reporting interval 1 to 720 hours; the domain refuses the rest with a 400 that says why.
- **Filtering on the judgment is done in memory** over the tenant's policies (the list reads the trend window of each, four at a time, in order), since it is not stored. A tenant has tens or hundreds of policies, not millions; if that stops being true the verdict would have to be stored, which this ADR deliberately does not do.

## Consequences
- Positive: always current, nothing to schedule, easy to explain and to audit by hand.
- Negative: a read costs a query per policy; the straight line is wrong for anything that is not roughly linear; a very busy tenant makes the list slower.
