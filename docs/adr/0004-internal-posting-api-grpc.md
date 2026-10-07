# ADR-0004: transfer-service calls account-service over gRPC; postings are idempotent on a caller-chosen id

- **Status:** Accepted
- **Date:** 2026-10-07

## Context

transfer-service (week 2) must look up the two accounts of a transfer and post the money movement in account-service,
which owns the ledger. The call is internal: it is never routed through the gateway and has exactly one client today.
The project guide asks for one gRPC call between transfer and account. A transfer whose posting times out must be
retried later with the same posting id (week 3 saga) without moving the money twice.

## Decision

**gRPC, using the support built into Spring Boot 4.1** (verified on Boot 4.1.1: `spring-boot-starter-grpc-server` and
`-client`, Spring gRPC 1.1.1, grpc-java 1.83.1, protobuf-java 4.35.1, all managed by the Boot BOM; protoc and the gRPC
plugin come from `io.github.ascopes:protobuf-maven-plugin`, configured by `spring-boot-starter-parent`; Apache-2.0 and
BSD-3 licences).

- **Contract module:** `contracts-grpc/` holds `corebank/ledger/v1/ledger_service.proto` and the generated stubs. It is
  separate from `contracts/` so that event consumers such as `aml-monitor` do not pull in gRPC. Generated code is
  excluded from SpotBugs and JaCoCo; the `.proto` file is the reviewed artifact. A breaking change opens a `v2`
  package, as for events.
- **API:** `GetCustomerAccount`, `FindCustomerAccountByIban`, `PostTransfer`. Lookups only see customer accounts (the
  bank's own accounts are reported as `NOT_FOUND`) and return no holder name, TCKN or balance. Money travels as a
  two-decimal string with a currency code, never a float.
- **Errors:** standard status codes with a `google.rpc.ErrorInfo` (domain `account-service`, reason = stable error
  code). `GrpcExceptionTranslator` is the single mapping, the twin of the HTTP handler; see *Error translation*.
- **Idempotency:** a new `posting` table (one row per posting, `V5`) whose primary key is the caller's posting id.
  `LedgerPostingService` claims the id first with `INSERT ... ON CONFLICT (id) DO NOTHING`. PostgreSQL makes a second
  inserter of the same id wait until the first transaction commits (then the retry reports `already_posted` with the
  original time) or rolls back (then the retry posts). A reused id with different accounts, amount or type is
  rejected. The header is append-only and must have at least two lines at commit, like the ledger itself (ADR-0002).
  The claim and the lines are written in one transaction (`LedgerPostingService` joins the caller's transaction with
  `MANDATORY`); the deferred `posting_has_entries_tg` trigger refuses to commit a header without lines, so a claim can
  never outlive a rejected posting.
- **Time precision:** the application `Clock` ticks in microseconds, the precision PostgreSQL stores, so the
  `posted_at` returned by the first call equals the one a retry reads back on every platform (the JDK clock has
  nanosecond digits on Linux). Production code takes time only from the injected `Clock`; ArchUnit enforces it.
- **Ports:** account-service HTTP `8080`, gRPC `9090` (plaintext; internal network only), transfer-service HTTP `8081`.
- **Reflection** (the gRPC service that lists every service and message) is off by default and on only in the `local`
  profile, for tools such as `grpcurl`; health checking stays on.

## Error translation

| Cause (exception) | gRPC status | `ErrorInfo.reason` | Outcome for the caller |
| --- | --- | --- | --- |
| Malformed request: id not a UUID, amount not a two-decimal string (`InvalidGrpcRequestException`) | `INVALID_ARGUMENT` | `INVALID_REQUEST` | Definite rejection |
| Invalid IBAN (`InvalidIbanException`) | `INVALID_ARGUMENT` | `INVALID_IBAN` | Definite rejection |
| Unknown currency code (`UnsupportedCurrencyException`) | `INVALID_ARGUMENT` | `UNSUPPORTED_CURRENCY` | Definite rejection |
| Amount not positive (`InvalidAmountException`) | `INVALID_ARGUMENT` | `INVALID_AMOUNT` | Definite rejection |
| Transfer currency differs from an account's (`CurrencyMismatchException`) | `INVALID_ARGUMENT` | `CURRENCY_MISMATCH` | Definite rejection |
| Account missing, or not a customer account (`AccountNotFoundException`) | `NOT_FOUND` | `ACCOUNT_NOT_FOUND` | Definite rejection |
| Not enough money (`InsufficientFundsException`) | `FAILED_PRECONDITION` | `INSUFFICIENT_FUNDS` | Definite rejection |
| Account closed (`AccountNotActiveException`) | `FAILED_PRECONDITION` | `ACCOUNT_NOT_ACTIVE` | Definite rejection |
| Bank's own account in a transfer (`AccountNotEligibleForPostingException`) | `FAILED_PRECONDITION` | `ACCOUNT_NOT_ELIGIBLE` | Definite rejection |
| Debit and credit account are the same (`InvalidPostingException`) | `FAILED_PRECONDITION` | `INVALID_POSTING` | Definite rejection |
| Posting id reused for a different movement (`PostingConflictException`) | `ALREADY_EXISTS` | `POSTING_ID_CONFLICT` | Not definite: a caller bug; never fail the transfer on it |
| Optimistic lock conflict (`OptimisticLockingFailureException`) | `ABORTED` | `CONCURRENT_MODIFICATION` | Not definite: retry with the same posting id |
| Any other exception on the server | `INTERNAL` | `INTERNAL_ERROR` | Unknown outcome |
| No answer: deadline, connection lost, server down, proxy error | `DEADLINE_EXCEEDED`, `UNAVAILABLE`, `CANCELLED`, `UNKNOWN`, ... (no `ErrorInfo`) | - | Unknown outcome |

**Rule for callers:** a response is a definite rejection only if its status is `INVALID_ARGUMENT`, `NOT_FOUND` or
`FAILED_PRECONDITION` **and** it carries an `ErrorInfo` from domain `account-service`; only then may transfer-service
move a transfer to `FAILED` and release its daily limit. Every other status, explicitly including `INTERNAL` and
`UNKNOWN`, and any status without that `ErrorInfo` (for example one produced by the gRPC library or a proxy) means the
posting may or may not have happened: the transfer stays `APPROVED` and is retried with the same posting id, which
`PostTransfer` makes safe. `ErrorCategoryMappingTest` and `GrpcExceptionTranslatorTest` fail if a new error category
has no mapping.

## Alternatives

| | gRPC (chosen) | REST + OpenAPI |
| --- | --- | --- |
| Contract | `.proto`, client and server generated, compile-time checked | OpenAPI document, client written or generated separately |
| Spring Boot 4.1 support | First-class starters, in-process test transport without ports | First-class (`RestClient`, HTTP interfaces) |
| Testing the caller | In-process fake server implementing the generated base class | WireMock |
| Cost | Second port, code generation, generated code excluded from gates | None beyond today's stack |
| Error model | Status codes plus `ErrorInfo` | Problem details (already used) |

REST would have been workable. gRPC was chosen because the guide asks for it, Boot 4.1 removes most of its setup cost,
and a generated, versioned contract fits a call that both sides must agree on exactly.

## Consequences

- A `PostTransfer` retry after a timeout is always safe; `LedgerGrpcServiceIT` sends 16 identical requests in parallel
  and the money moves once. A mutation test (making the claim always succeed) fails three of its tests.
- Account lookups for transfers go through gRPC, not the public REST API.
- The gRPC port has no TLS and no authentication yet; securing internal traffic belongs to the deployment and
  Keycloak work (weeks 4 and 6). Spring gRPC supports TLS through SSL bundles.
- Every posting now has a header row; existing postings were backfilled by `V5`.
