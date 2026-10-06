# CLAUDE.md

Context for AI assistants (and humans) working in this repository.

## Scope

- **Scope is P1 only** (mini core banking: account, transfer, ledger services), plus the P1 items that prepare for
  the later transaction monitoring component (P4). P2, P3 and P5 are out of scope.
- The binding project guide is `docs/private/proje-rehberi.md` (git-ignored; ask the owner if it is missing).
- Follow the roadmap: do only the step that was asked for, nothing ahead of it.

## Architecture

- Maven multi-module monorepo, Java 21, Spring Boot 4.1.x, Spring Cloud 2025.1.x (BOM only so far).
- `contracts/`: versioned JSON Schemas and generated DTOs. The only shared code. See ADR-0001.
- `services/account-service/`: accounts, double-entry ledger, balances, transactional outbox. PostgreSQL + Flyway.
- `services/{gateway,customer-service,transfer-service,audit-service,notification-service}/`: skeletons; each gets its
  dependencies in the roadmap week that implements it.
- Reserved for P4: `services/aml-monitor/` and `tools/synthetic-generator/` (do not create them yet).
- Infrastructure for local runs: `docker-compose.yml` (PostgreSQL, Kafka in KRaft mode, topic creation).

### Layers inside a service

```
api  ->  application  ->  domain
              ^
infrastructure (implements application ports)
```

- `domain`: plain Java. Depends on nothing but the JDK (enforced by ArchUnit).
- `application`: use cases and ports (interfaces). Depends only on the domain; the application layer may use Spring's
  `@Service` and `@Transactional` annotations, a deliberate trade-off that keeps transaction boundaries visible on the
  use case without an extra decorator layer, and nothing else from Spring.
- `api`: HTTP translation only (request -> command, result -> response). One global exception handler.
- `infrastructure`: JPA/JDBC adapters, outbox, configuration. Only the outbox adapter knows the event contracts.

## P4 integration rules

- Events go to versioned topics (`banking.<area>.<event>.v<N>`); a breaking change means a new schema and topic.
- Every event has the envelope `eventId, eventType, schemaVersion, occurredAt, correlationId, actorUserId`.
- Partition key is the account id. Topics keep data for days; never assume a single consumer.
- Transfer events carry both accounts, beneficiary name, amount, currency, channel, makerUserId and checkerUserId
  (null below the approval threshold). Events never carry a TCKN.
- Events are written to the outbox in the same transaction as the business change.

## Code rules (SOLID and Clean Code are binding)

- Single responsibility: controller = HTTP, service/domain = rules, repository/adapter = persistence.
- Open/closed: add behaviour by adding classes (e.g. domain exceptions pick an `ErrorCategory`; the handler is untouched).
- Interface segregation: narrow ports (`AccountReader`, `AccountWriter`, `BalanceReader`, `LedgerReader`, `LedgerWriter`).
- Dependency inversion: infrastructure behind interfaces, constructor injection only (no field injection).
- Intention-revealing names; no abbreviations or single-letter names (loop counters excepted).
- Small methods, guard clauses, no magic numbers or strings: use configuration or named constants.
- Value objects for domain concepts: `Money`, `Iban`, `Tckn`, `HolderName`, typed ids. No primitive obsession.
- Never swallow exceptions; throw meaningful domain exceptions.
- Comments explain *why*, not *what*.

## Forbidden patterns

- `double` or `float` for money (Checkstyle fails the build). Use `Money` / `BigDecimal`, scale 2, `HALF_EVEN`.
- Secrets in code or committed config. Use `.env` (git-ignored) and document variables in `.env.example`.
- TCKN or unmasked IBAN in logs, exception messages or events (`Tckn` and `Iban` mask themselves in `toString`).
- Real personal data. Only synthetic values (Faker with Turkish locale, algorithm-valid synthetic TCKN/IBAN, the
  synthetic bank code 99999).
- Updating or deleting ledger rows. Corrections are new postings (the database enforces this too).

## Tests

- Every rule has a test. Domain and application: JUnit 5 + AssertJ with in-memory fakes of the ports.
- API: `@WebMvcTest` slices. Persistence and messaging: Testcontainers (`*IT`, run by failsafe).
- Architecture: `ArchitectureTest` (ArchUnit) guards the layer rules.
- `./mvnw verify` runs Checkstyle, PMD/CPD, SpotBugs, unit tests, integration tests and the JaCoCo gate (80% lines).
  Any violation fails the build. AI-written code goes through the same gates.

## Workflow

- Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `build:`, `ci:`). `main` is protected; every change via PR.
- No AI attribution in commit messages or PR descriptions: no `Co-Authored-By`, no `Claude-Session`, no
  "Generated with Claude Code" lines. AI involvement is recorded in `docs/ai-workflow.md` instead.
- Record notable AI mistakes and how tests caught them in `docs/ai-workflow.md`.
- Check every new dependency for version and licence before adding it.
