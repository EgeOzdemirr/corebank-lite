-- Funding accounts no longer store a balance (ADR-0003). Every opening deposit used to update the one funding row
-- per currency, so concurrent openings collided on its version. Their balance is now the signed sum of their ledger
-- lines; customer accounts keep a materialised, row-locked balance.
ALTER TABLE account ALTER COLUMN balance DROP NOT NULL;
ALTER TABLE account DROP CONSTRAINT account_owner_matches_type_ck;

-- Lossless: the ledger already holds every movement of these accounts.
UPDATE account SET balance = NULL WHERE account_type = 'FUNDING';

ALTER TABLE account ADD CONSTRAINT account_owner_matches_type_ck CHECK (
    (account_type = 'CUSTOMER' AND customer_id IS NOT NULL AND holder_tckn IS NOT NULL
        AND balance IS NOT NULL AND balance >= 0)
    OR (account_type = 'FUNDING' AND customer_id IS NULL AND holder_tckn IS NULL AND balance IS NULL));

-- Serves the derived balance with an index-only scan: the ledger is append-only, so its pages stay all-visible
-- after vacuum and the sum never has to visit the table.
CREATE INDEX ledger_entry_account_amount_ix ON ledger_entry (account_id) INCLUDE (direction, amount);
