# ADR-0003: Funding balances are derived from the ledger; customer accounts are row-locked in id order

- **Status:** Accepted
- **Date:** 2026-10-07

## Context

Every opening deposit is a posting from the currency's single funding account to the new customer account. Both
balances were materialised on the `account` row and guarded by `@Version`, so every concurrent opening in one currency
updated the same funding row. `HotAccountConcurrencyIT` reproduced it: of 200 openings sent by 16 parallel clients,
only 20 to 21 succeeded and the rest received HTTP 409. Nothing was lost (the ledger summed to zero and the funding
balance matched the committed deposits), but nine out of ten requests were rejected. Transfers from week 2 on will
hit the same pattern on busy customer accounts.

## Options

| Option | Effect on the funding row | Effect on busy customer accounts | Verdict |
| --- | --- | --- | --- |
| **A.** `SELECT ... FOR UPDATE` in account id order | Every opening queues on one row lock; no 409, but throughput of a currency is capped by one row | No 409 and no deadlock; writers on the same account queue | Right for customer accounts |
| **B.** Optimistic lock plus bounded retry | Under the measured load about one attempt in ten wins, so a bounded retry still ends in 409 and burns work on every lost round | Same, plus retry storms when an account is busy | Rejected |
| **C.** Do not materialise institution balances; derive them from the ledger | The row is never written, so there is nothing to contend for | Not applicable: a customer balance must be checked before every debit | Right for funding accounts |

## Decision

C and A together.

- **Funding accounts** (`AccountType.FUNDING`) do not store a balance (`V4`: `balance` is `NULL`, enforced by
  `account_owner_matches_type_ck`). A posting validates them (exists, active, same currency) but never locks or updates
  their row. Their balance is the signed sum of their ledger lines, served by `JdbcBalanceReader`. They may go negative
  by design, so no balance check is lost.
- **Customer accounts** keep a materialised balance. `LedgerPostingService` locks them one by one in `AccountId` order
  through the `AccountLocker` port. Hibernate issues `SELECT ... FOR NO KEY UPDATE`: it serialises writers of the same
  row like `FOR UPDATE`, but does not block the `KEY SHARE` lock that the foreign key check of each new `ledger_entry`
  row takes, so inserting ledger lines never waits on a balance lock. `@Version` stays as a second guard against any
  writer that does not take the lock.
- **Index:** the derived sum reads `ledger_entry` by `account_id`. The existing `ledger_entry_account_posted_ix`
  already leads with `account_id`; `V4` adds `ledger_entry_account_amount_ix (account_id) INCLUDE (direction, amount)`
  so the sum is an index-only scan (the ledger is append-only, so its pages stay all-visible after vacuum). At high
  volume, a periodic balance snapshot per account (sum up to a cut-off plus the lines after it) is the alternative
  that keeps the read cost bounded.

## Evidence

`HotAccountConcurrencyIT`, same machine, PostgreSQL 18 in Testcontainers, coverage agent off, 16 parallel clients:

| Scenario | Before (materialised funding balance, `@Version` only) | After (this ADR) |
| --- | --- | --- |
| 200 openings with deposit | 20-21 × 201, 179-180 × 409, 573-631 ms (4 runs) | 200 × 201, 565-585 ms (3 runs) |
| 400 transfers, pairs A-B, C-D, A-C, B-D in both directions at once | not run (no lock order on the posting path yet) | 400 committed, 0 failures, 0 deadlocks (SQLState `40P01`), every stored balance equals the ledger-derived and the expected balance, ledger total 0 |

The cost of A is queueing. 200 transfers between **one hot pair** took 407-431 ms; the same 200 transfers spread over
**8 disjoint pairs** took 93-94 ms. Postings on one account run one after another (here roughly 2 ms each, a little
under 500 per second per account), while postings on different accounts run in parallel. A single extremely busy
customer account (a merchant, a payroll account) is bounded by that rate; that is the price of checking its balance
exactly.

## Consequences

- The 409 on account opening is gone, and a funding account's balance is always consistent with the ledger by
  construction; the nightly reconciliation (later week) only has materialised customer balances to compare.
- Reading a funding balance costs a sum over its ledger lines, which grows with the number of postings; snapshots
  (above) are the planned answer if it becomes slow.
- `Account.balance()` exists only for types that materialise it; other callers ask `BalanceReader`.
- A new account is flushed when it is added (`saveAndFlush`), so a posting later in the same transaction can lock it.
- Deadlock freedom depends on every posting path locking through `LedgerPostingService`; the transfer posting API
  (week 2) reuses it.
