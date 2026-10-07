# ADR-0005: Transfer lifecycle across a remote posting

- **Status:** Accepted
- **Date:** 2026-10-07

## Context

transfer-service records transfers in its own database, but the money moves in account-service, over gRPC
(ADR-0004). A database transaction cannot span both. A posting call can also end without an answer (deadline, lost
connection, crash), and then nobody knows whether the money moved. Above the approval threshold a second user must
decide, maker and checker must differ, and every transfer counts against a daily limit per source account.

## Decision

**Short local transactions, remote call in between.** `TransferRecorder` holds the transactions; the orchestrating
services never open one around the gRPC call:

1. Validate (currency, single limit, account lookups) - nothing is written yet.
2. Transaction: reserve the daily limit, insert the transfer (PENDING_APPROVAL or APPROVED), outbox events.
3. If APPROVED: `PostTransfer` with the transfer id as posting id, outside any transaction.
4. Transaction: record the definite result (POSTED, or FAILED with the limit given back), outbox events.

**Posting outcomes** (classification rule: ADR-0004):

| Outcome | Transfer | Daily limit | HTTP after create / approval |
| --- | --- | --- | --- |
| Posted (also "already posted") | POSTED | kept | 201 / 200 |
| Definite rejection (`INVALID_ARGUMENT`, `NOT_FOUND`, `FAILED_PRECONDITION` with account-service's ErrorInfo) | FAILED, `failureCode` = the error code | given back | 201 / 200 |
| Unknown (`INTERNAL`, `UNKNOWN`, deadline, `UNAVAILABLE`, no ErrorInfo, retries or budget spent) | stays APPROVED | kept | 202 / 202 |
| `POSTING_ID_CONFLICT` | stays APPROVED, ERROR log, row in `transfer_review` | kept | 202 / 202 |

A 201 means *recorded and finished*, **not** *succeeded*: clients must read `status` (OpenAPI and README say so). A
202 means *recorded, not finished*: PENDING_APPROVAL waits for a checker, APPROVED waits for recovery.

**Why an unknown outcome never fails the transfer:** if the money did move and the transfer were marked FAILED, the
client would reasonably send it again and pay twice, and the limit would be given back for money that left. Keeping
it APPROVED with the limit reserved errs on the safe side; recovery (roadmap week 3) repeats `PostTransfer` with the
same posting id, which account-service makes idempotent.

**Retries:** only `ABORTED` (`CONCURRENT_MODIFICATION`) is retried, with Spring's `RetryTemplate`: exponential backoff
(100 ms, x2, at most 1 s, at most 3 retries) and a **total posting budget** (default 5 s) that covers all attempts and
waits. The budget is enforced twice: the retry policy's timeout stops further retries, and every call's gRPC deadline
is the smaller of the call deadline (2 s) and what is left of the budget. A spent budget is an unknown outcome.
`POSTING_ID_CONFLICT` is never retried and automatic recovery must skip every transfer in `transfer_review`.

**Daily limit:** `daily_transfer_usage` holds the running total per source account, Istanbul business day and
currency. Check and increment are one statement (`INSERT ... SELECT ... WHERE ... ON CONFLICT DO UPDATE ... WHERE`), so
concurrent requests cannot overshoot together. Every transfer counts except FAILED ones (REVERSED keeps its usage, so a
reversal cannot win limit back). The move to FAILED gives the amount back to the business day stored with the transfer,
not to today.

**Approval:** the maker can neither approve nor reject; the database also refuses `checker = maker`. A rejection is
FAILED/`REJECTED_BY_CHECKER` with the rejecting user as `checkerUserId`. Decisions lock the transfer row, so an approval
and a rejection arriving together run one after the other and the second gets 409. Approval must happen within the
Istanbul business day of the request ("strictly greater than the threshold" needs approval; equal does not). A decision
arriving later expires the transfer instead (FAILED/`APPROVAL_EXPIRED`, no checker, limit back); that expiry is
committed in its own transaction and only then is 409 `APPROVAL_EXPIRED` returned, so the 409 never undoes it. The
event's actor is the user whose request triggered the expiry.

## Consequences

- No database connection or row lock is held while account-service is called; a slow account-service costs at most
  the posting budget per request.
- A transfer can stay APPROVED for a while; until recovery exists (week 3) such transfers need manual follow-up, and
  their limit stays reserved meanwhile.
- `TransferRecorder` and `TransferPostingStep` log (WARN for unknown outcomes, ERROR for posting id conflicts); the
  application layer may use SLF4J, which is not Spring.
- `ConcurrentDecisionIT` (approve and reject at once, exactly one wins), `ApprovalExpiryIT` (expiry committed before
  the 409) and 30 parallel transfers against the daily limit (exactly 25 fit) back these rules.

## Limits

- **Expiry runs only when somebody acts on the transfer.** A scheduled job that expires waiting transfers at the end
  of the business day comes in roadmap week 3, together with the representation of a fixed system actor in the event
  envelope (`actorUserId`; `checkerUserId` stays null for expiries).
- **No automatic recovery yet** for transfers left APPROVED after an unknown outcome (week 3, same posting id).
- **`POSTING_ID_CONFLICT` has a log line and a table row, but no metric or alarm** until observability (week 5).
- The acting user comes from `X-Actor-User-Id` (required on writes) until JWT authentication (week 4).
