# AI-assisted development log

For each larger feature: what the AI was given, what it got wrong, and how a test or quality gate caught it.

## Week 1: monorepo, contracts, account-service

- **Given:** the private project guide (P1 scope), the file plan approved by the owner, and the decisions on package
  root, opening deposit, transfer schemas and gitleaks.
- **Wrong:** declared the transfer channel enum as a standalone schema with an explicit `javaType`. jsonschema2pojo
  then treated the class left over in `target/classes` as "already existing" and skipped generating it on
  incremental builds. **Caught by:** running the contracts build three times in a row (it failed every other run);
  fixed by inlining the enum.
- **Wrong:** named the HTTP filter `RequestContextFilter`, which clashes with a bean Spring MVC registers under the same
  name. **Caught by:** the `@WebMvcTest` slice failing to start; renamed to `RequestContextHeaderFilter`.
- **Wrong:** added `errorCode` to validation problem details before the base handler had created the body, so it was
  missing. **Caught by:** `AccountControllerTest` asserting `$.errorCode` on a validation error.
- **Wrong:** asserted on the top-level exception message for a commit-time database error; the trigger message is in
  the root cause. **Caught by:** `LedgerIntegrityIT`.
- **Quality gates that pushed back:** SpotBugs flagged a response record exposing a mutable list (now copied
  defensively); PMD flagged a field that could be final (`Account.status`).

## Week 2: transfer-service, state machine, Redis idempotency, limits

- **Given:** the private project guide (week 2 scope), the PR split and file plans approved by the owner, and the
  owner's decisions on gRPC, hot-account locking, unknown posting outcomes (HTTP 202, transfer stays `APPROVED`),
  idempotency key release, daily limit scope and ports.
- **Wrong (week 1 code):** the append-only guard on `ledger_entry` was a row-level `UPDATE`/`DELETE` trigger only, so
  `TRUNCATE ledger_entry` (and `TRUNCATE account CASCADE`) emptied the ledger. **Caught by:** reviewing the triggers
  while writing ADR-0002; two new `LedgerIntegrityIT` tests failed against the old schema and pass with the V3
  statement-level `BEFORE TRUNCATE` trigger.
- **Hot funding account (ADR-0003):** the red test came first and measured the week 1 design (20 of 200 parallel
  openings succeeded, 180 got HTTP 409). **Wrong:** the first version of the fix added an `Account.materialisesBalance()`
  shortcut on top of the new accessors and a nullable assignment in the JPA entity. **Caught by:** PMD
  (`TooManyMethods`, `NullAssignment`); the aggregate now exposes `materialisedBalance()` as an `Optional`.
- **Wrong:** a bulk rename of that shortcut produced a lambda parameter that shadowed a local variable in a test fake.
  **Caught by:** the compiler on the next build. **Wrong:** the concurrency test caught `RuntimeException`.
  **Caught by:** Checkstyle `IllegalCatch`; it now catches only data access, transaction and domain exceptions, so an
  unexpected error fails the test.
- **Checked rather than assumed:** the ADR first said `SELECT ... FOR UPDATE`; SQL logging showed Hibernate emits
  `FOR NO KEY UPDATE` on PostgreSQL, and the ADR explains why that is the better lock here.
- **Transfer event contracts:** no gate pushback. Repeated the week 1 check of building `contracts` three times in a
  row without `clean`; the generated `TransferRequested*` and `TransferFailed*` classes were present every time.
- **gRPC posting API (ADR-0004):** **Wrong:** kept the ArchUnit rule "only the outbox knows `contracts..`", which
  also matched the new gRPC stubs. **Caught by:** `ArchitectureTest`; split into one rule for event contracts and one
  for gRPC contracts. **Wrong:** an `EnumMap` for the status mapping. **Caught by:** PMD `UseConcurrentHashMap`;
  replaced by an immutable `Map.of`. SpotBugs flagged 23 issues in protoc output, which is now excluded like the
  generated event DTOs.
- **Checked rather than assumed:** the parallel-retry test passed on the first run, so its power was checked by a
  mutation (the posting claim always succeeding): three `LedgerGrpcServiceIT` tests failed, then the code was restored.
- **Wrong:** used `Clock.systemUTC()` as is. A retried `PostTransfer` returned `posted_at` read back from PostgreSQL
  (microseconds) while the first call returned the in-memory instant. **Caught by:** CI only: on Linux the JDK clock
  has nanosecond digits (`.217269213` vs `.217269000`), on macOS it has microseconds (measured: 0 of 100,000 instants
  with finer digits), so the local run could never fail. Fixed at the source with a microsecond `Clock.tick`; the
  regression test uses a fixed clock with nanoseconds so it fails on every platform, and a new ArchUnit rule makes the
  injected `Clock` the only source of time. **Lesson:** a test that depends on platform behaviour needs an input that
  forces the behaviour, not the platform's default.
- **Review follow-ups on the gRPC PR:** each new test was checked for power by a temporary mutation: moving the
  posting claim into its own transaction fails all `LedgerGrpcServiceIT` tests (the deferred trigger refuses a header
  without lines), and enabling reflection by default fails `reflection_isOffByDefault`. The error table in ADR-0004
  is backed by an end-to-end test for every row a transfer can reach.
- **Transfer domain (week 2, PR 5):** **Wrong:** the first state machine was a `switch` with `||` conditions and
  `Transfer.restore` took seven parameters. **Caught by:** Checkstyle (`CyclomaticComplexity`, `ParameterNumber`);
  the state machine is now one table of allowed steps, and persistence goes through a `TransferSnapshot`. **Wrong:**
  a first draft of a Javadoc contradicted itself about the Istanbul day boundary. **Caught by:** reading the diff
  before running the build; `BusinessCalendarTest` pins the 21:00 UTC boundary. **Wrong:** after a temporary
  ArchUnit violation, `git checkout` was used to undo it on a file that was not yet tracked, so the revert silently
  did nothing. **Caught by:** checking the file afterwards; untracked files are now restored by reversing the edit.
