# Recovery queries & invariants

This doc collects the “hard parts” of RhytmicRiddles: recovery, interrupts, and invariant queries.

It is intentionally close to the code:

- `Interrupt` table represents interrupts (team buzzes and technical pauses)
- recovery derives seek + UI scenario from persisted timestamps

## Invariants (assumptions the code relies on)

1. At most one **active** team interrupt should exist at a time per schedule (no overlapping unresolved interrupts).
2. Teams are not allowed to buzz while there is an unresolved interrupt.
3. An interrupt (socket disconnect or team answering) can only be resolved when both apps are connected.
4. All unresolved interrupts are resolved at the same time.
5. These rules make certain overlap patterns impossible and simplify seek computation.

## Notes from Phase-2 design

### Seek computation

`InterruptEntity.findInterrupts` retrieves all current-schedule interrupt frames with
a simple chronological query. The query no longer filters out nested frames:
`PlaybackTimeline.seekSeconds` merges every overlap once in Java, whether two
interrupts are nested, partially overlapping, disjoint or still open.

`InterruptServiceImpl.calculateSeek` combines those frames with the TV-registered
`playback_delay` timestamp intervals, clips them to `[started_at, now]`, subtracts
their **union** from server elapsed time and returns seconds. The table stores
only interval boundaries (`sent_at`, `acknowledged_at`); it has no derived `delay`
column. The initial interval is registered by the TV before playing audio, not by
the backend during a category/schedule transition.

See [State machine & recovery](state-machine-and-recovery.md#tv-playback-admin-timer-and-recovery).

## What to test (high-value)

- reconnect during:
    - lobby
    - album selection (none picked / picked but not started)
    - song playing (no interrupts)
    - team answering (team interrupt active)
    - technical pause active
    - song ended with no answer (UI shows replay/reveal)
- ensure seek does not jump forward or replay missing seconds
