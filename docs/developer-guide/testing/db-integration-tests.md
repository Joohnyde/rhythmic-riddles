# Database Integration Tests

Database integration tests protect behavior that depends on real PostgreSQL query semantics or on multiple persistence and service layers composing correctly.

## Principles

- Use real PostgreSQL. Do not substitute H2 for persistence behavior exercised here.
- Seed state directly with `QuizPersistenceFixture` SQL so repository tests do not accidentally prove `save()` instead of the query under test.
- Keep repository suites focused on ordering, filtering, projections, scoping, updates, and PostgreSQL-specific SQL. Trivial Spring Data CRUD does not belong here.
- Keep service integration suites focused on persistence-to-domain composition such as recovery, room ownership, transaction atomicity, and interrupt invariants.
- Use a fixed `Clock` whenever wall-clock time changes an assertion.
- Ordinary repository/recovery tests remain transactional through `@DataJpaTest` and roll back after each test.
- Tests that must observe independent transactions explicitly disable the test transaction and clean the shared ephemeral database before and after each test.
- Build the ephemeral database from the current production initializer layers: `db_01_create_schema.sql` followed by `db_04_add_runtime_invariants.sql`. Hibernate schema generation is disabled.
- Do not load `db_03_fill_tables_with_initial_data.sql` in DB integration tests; each test owns its fixture data.
- Concurrency tests coordinate independent service calls with barriers/latches and virtual threads. They assert persisted invariants, not arbitrary timing or which team wins a fair race.

## Database script policy exercised here

The current pre-release database policy is intentionally simple:

- `db_01` owns the base schema and intrinsic rules, including unique room codes;
- `db_04` owns the additional runtime invariants introduced during foundation work;
- the same invariant should not be duplicated in both scripts;
- shipped scripts will be frozen at the first customer release;
- post-release schema upgrades will later use a dedicated migration/versioning mechanism.

The DB integration initializer verifies the current `db_01 -> db_04` composition only. It does **not** test future migration/version tracking, the packaged `.cestereg_sql_done` marker, bundled `psql`, or installer upgrades. Those remain release/package concerns.

## Structure

```text
src/test/java/com/cevapinxile/cestereg/
├── persistence/integration/
│   ├── DatabaseSchemaIntegrationTest.java
│   └── support/
│       ├── DatabaseTestCleaner.java
│       ├── EmbeddedPostgresTestDatabase.java
│       ├── FixedTestClockConfiguration.java
│       ├── PostgresJpaIntegrationTest.java
│       ├── ProductionSchemaSql.java
│       └── QuizPersistenceFixture.java
├── persistence/repository/integration/
│   ├── GameRepositoryIntegrationTest.java
│   ├── InterruptRepositoryIntegrationTest.java
│   ├── ScheduleRepositoryIntegrationTest.java
│   ├── CategoryRepositoryIntegrationTest.java
│   └── TeamRepositoryIntegrationTest.java
└── core/service/integration/
    ├── GameLifecycleIntegrationTest.java
    ├── GameRecoveryIntegrationTest.java
    ├── GameStateAtomicityIntegrationTest.java
    ├── InterruptInvariantIntegrationTest.java
    ├── RoomOwnershipIntegrationTest.java
    ├── ScheduleAtomicityIntegrationTest.java
    ├── ScoreCacheConsistencyIntegrationTest.java
    ├── TransactionAtomicityIntegrationTest.java
    └── concurrency/
        ├── GameConcurrencyIntegrationTest.java
        └── InterruptConcurrencyIntegrationTest.java
```

`EmbeddedPostgresTestDatabase` starts one ephemeral PostgreSQL process for the test JVM and applies `db_01` followed by `db_04`. `ProductionSchemaSql` is the shared locator/reader for these repository-level SQL scripts.

The ordinary JPA integration suites use transaction rollback for isolation. Transaction-boundary, cache-consistency, and concurrency suites deliberately opt out when the behavior under test requires service-owned transactions to finish independently; `DatabaseTestCleaner` gives those suites an empty database before and after each test. The overall DB integration layer remains intentionally sequential because the PostgreSQL process is shared.

## Interrupt concurrency semantics

Interrupt concurrency deliberately distinguishes user/game commands from must-persist system events:

- two teams buzzing simultaneously may produce at most one unresolved team-answer interrupt;
- a team buzz uses the fail-fast same-room lock semantics so a losing team does not queue and answer later;
- a system interrupt is a physical/runtime fact and must not be discarded because another room transaction is briefly in flight;
- if a team transaction already owns the room, the system interrupt waits and is persisted after that transaction commits;
- if the system interruption already owns the room, a competing team buzz fails fast;
- a system pause created while a team is already answering is allowed to nest without resolving the outer team interrupt, and `contextFetch()` must still reconstruct that layered state.

This protects the domain invariant that supported paths produce disjoint or fully nested interrupt intervals rather than arbitrary partial overlaps.

## Running locally

The project selects Zonky PostgreSQL native binaries through platform profiles:

```bash
# Linux
mvn -Dplatform=linux test

# Windows
mvn -Dplatform=windows test

# macOS
mvn -Dplatform=macos test
```

Run only the DB integration layer during development with:

```bash
mvn -Dplatform=linux \
  -Dtest='*RepositoryIntegrationTest,*RecoveryIntegrationTest,*InvariantIntegrationTest,*OwnershipIntegrationTest,*AtomicityIntegrationTest,*SchemaIntegrationTest,*ConcurrencyIntegrationTest,*LifecycleIntegrationTest,*ConsistencyIntegrationTest' \
  test
```

## Current coverage

Repository suites cover stage-aware game lookup, room-deletion cascade behavior, recovery-critical interrupt interval semantics, independent team/system interrupt retrieval, error resolution, correct-answer/history queries, schedule ordering and scoping, album/team client projections, category progression, PostgreSQL score projections, and automatic picker selection including the valid null/admin-fallback contract.

Recovery covers lobby, album selection, song playback, and winner state. Song recovery includes active team answering, active technical pause, ended-but-unrevealed, revealed states with and without a correct team, nested/disjoint pause timing, and representative incomplete persisted state. The layered team/system case now creates the system pause through the real `InterruptService` before verifying recovery, so persistence and reconstruction are protected together.

Additional suites protect persisted lifecycle transitions, room ownership for externally supplied identifiers, rollback of multi-write operations, PostgreSQL/score-cache coherence, the current `db_01 + db_04` initializer composition, simultaneous team buzzes and duplicate answers, system-vs-team lock ordering, category allocation races, and duplicate progress requests.

All new tests must also be registered in `test-catalog.csv`; `TestCatalogConsistencyTest` enforces that catalog/code contract.
