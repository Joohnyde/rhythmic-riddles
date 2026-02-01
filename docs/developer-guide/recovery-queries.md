# Recovery queries & invariants

This doc collects the “hard parts” of RhytmicRiddles: recovery, interrupts, and invariant queries.

It is intentionally close to the code:
- `Odgovor` table represents interrupts (team buzzes and technical pauses)
- recovery derives seek + UI scenario from persisted timestamps

## Invariants (assumptions the code relies on)
1. At most one **active** interrupt should exist at a time per schedule (no overlapping unresolved interrupts).
2. Teams are not allowed to buzz while there is an unresolved interrupt.
3. A technical pause (socket disconnect) can only be resolved when both apps are connected.
4. These rules make certain overlap patterns impossible and simplify seek computation.

## Notes from Phase-2 design

### Overlap detection (conceptual)
The naive overlap query:

```sql
SELECT *
FROM Prekidi p1, Prekidi p2
WHERE p1.kraj >= p2.kraj OR p2.pocetak >= p1.pocetak
```

does not cover all cases, but with our invariants we can exclude some edge overlaps.

### “Outer frame” query (latest enclosing interrupt)
A useful pattern is: find interrupts that are not enclosed by a later “outer” interrupt:

```sql
SELECT i1.*
FROM interrupts i1
WHERE i1.start > ?
  AND NOT EXISTS (
        SELECT 1
        FROM interrupts i2
        WHERE i2.start > ?
          AND i2.id <> i1.id
          AND i2.begin <= i1.begin
          AND i2."end"   >= i1."end"
      );
```

### Seek computation sketch
Pseudo logic:

- `res = 0`
- `end = starting_timestamp`
- if no active interrupt: add a “closing frame”
- for each interrupt:
  - `res += interrupt.start - end`
  - `end = interrupt.end`
- return `res`

In code, `OdgovorService.findSeek()` is the source of truth. If you change it, update this doc and add tests.

### Concrete JPQL from notes
The project contains a JPQL variant of the “outer frame” selection that returns `InterruptFrame(begin,end)` for a schedule.

If you change entity names/columns, rewrite these carefully and add regression tests.

## What to test (high-value)
- reconnect during:
  - lobby
  - album selection (none picked / picked but not started)
  - song playing (no interrupts)
  - team answering (team interrupt active)
  - technical pause active
  - song ended with no answer (UI shows replay/reveal)
- ensure seek does not jump forward or replay missing seconds

