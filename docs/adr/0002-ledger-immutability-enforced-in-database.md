# ADR-0002: Ledger immutability is enforced in the database as well as in the application

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

The ledger is the bank's record of truth: balances can be rebuilt from it, reconciliation compares against it, and the
monitoring component (P4) trusts that a posted amount never changes afterwards. Two rules protect it. Every posting
balances (one currency, signed amounts sum to zero), and posted rows are never updated or deleted; a correction is a
new posting.

The domain already enforces both rules (`Posting` refuses unbalanced entries, `LedgerWriter` only appends). But the
application is not the only writer a database ever sees: manual SQL during an incident, a data-fix migration, a future
batch job or reporting tool, or a bug that slips past the domain model all write to the same tables.

## Decision

The database enforces the ledger rules on its own, so that a broken invariant fails loudly instead of being stored:

| Rule | Mechanism (Flyway) |
| --- | --- |
| A posting balances in a single currency | `ledger_entry_balanced_tg`: constraint trigger, `DEFERRABLE INITIALLY DEFERRED`, checked at commit because the legs are inserted one row at a time (V1) |
| No `UPDATE` or `DELETE` of ledger rows | `ledger_entry_append_only_tg`: row-level `BEFORE UPDATE OR DELETE` trigger (V1) |
| No `TRUNCATE`, also not through `TRUNCATE account CASCADE` | `ledger_entry_no_truncate_tg`: statement-level `BEFORE TRUNCATE` trigger (V3); row triggers do not fire for `TRUNCATE` |
| Amounts are positive, directions are known | `CHECK` constraints on `ledger_entry` (V1) |
| Customer accounts never go negative | `account_owner_matches_type_ck` (V1) |

`LedgerIntegrityIT` proves each rule by bypassing the application and issuing the SQL directly.

## Why both layers

- **The domain gives good errors, the database gives the guarantee.** The domain rejects a bad posting early with a
  meaningful exception and error code. The database is the last line that every writer has to pass, whatever its
  language or code path.
- **A wrong ledger is worse than a failed request.** A rejected write can be retried; a silently changed historic
  amount can only be found by an audit, if at all.
- **Audit evidence.** "Ledger rows cannot be changed" is a claim an auditor can test against the schema, without
  reading application code.

## Consequences

- Corrections are always compensating postings (for example a reversal), which keeps the full history.
- Test and maintenance tooling cannot clean the ledger with `DELETE` or `TRUNCATE`; tests use fresh containers.
- A balance check failure surfaces at commit as a `DataAccessException` whose root cause carries the trigger message.
- **Limits:** triggers do not stop the table owner or a superuser who disables them (`ALTER TABLE ... DISABLE
  TRIGGER`, `session_replication_role = replica`) or drops the table. Today the application connects as the schema
  owner. When deployment manifests arrive, the application should use a separate role without ownership and without
  `UPDATE`, `DELETE` or `TRUNCATE` privileges on `ledger_entry`, so that the triggers become the second line rather
  than the only one.
