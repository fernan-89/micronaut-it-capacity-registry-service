# ADR-034: A Level Is Left With Hysteresis

## Status
Accepted (refines the level of ADR-031)

## Context
ADR-031 took the level from the latest sample alone. A value hovering at a percentage (69.8, 70.2, 69.9, 70.1...) made the level flip between `OK` and `WARNING` on every read, and anything that reacts to the level (an alerting rule, a person looking at the screen) flapped with it.

## Decision
- **Still nothing stored.** The level is computed on every read by replaying the samples of the trend window, oldest first, through a small state machine. The latest sample counts even when the window does not hold it.
- **Rising is immediate**: a sample at or above a percentage enters that level at once, and may skip one (`OK` straight to `CRITICAL`).
- **Falling is held back**: a level is left only when the utilisation is below the percentage that entered it by `HYSTERESIS_POINTS` (5). `CRITICAL` stays down to `critical - 5`; then `WARNING` stays down to `warn - 5`; below that, `OK`. Exactly 5 below still stays.
- **The first sample has no past** and is judged plainly, so a window that starts mid-incident starts from what the value says.
- **The margin is a constant, not a field.** It is `min(5, warnPercent / 2)` so that a very low warning percentage can still be left. A per-policy margin would touch the API, the screens and every smoke script for a number nobody has asked to tune yet; if one does, it becomes an optional field with 5 as the default.
- **`utilisationPercent` stays the raw latest value.** Only `level` is held; the number a person reads is not smoothed.

## Consequences
- Positive: a value hovering at a threshold no longer flips the level; no schema, API or stored-data change.
- Negative: the level can read `WARNING` for a value that is plainly below the warning percentage (until it clears by 5 points), and the replay depends on the window: a sample older than `windowDays` no longer holds a level up. Both are what hysteresis means and are said in the README.
