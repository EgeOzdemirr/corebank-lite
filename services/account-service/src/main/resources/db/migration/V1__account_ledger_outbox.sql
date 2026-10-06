-- Account numbers below 1000 are reserved for the bank's own accounts (see V2).
CREATE SEQUENCE account_number_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE account (
    id           UUID PRIMARY KEY,
    iban         VARCHAR(26)    NOT NULL UNIQUE,
    account_type VARCHAR(16)    NOT NULL CHECK (account_type IN ('CUSTOMER', 'FUNDING')),
    customer_id  UUID,
    holder_name  VARCHAR(140)   NOT NULL,
    holder_tckn  VARCHAR(11),
    currency     VARCHAR(3)     NOT NULL,
    balance      NUMERIC(19, 2) NOT NULL,
    status       VARCHAR(16)    NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    opened_at    TIMESTAMPTZ    NOT NULL,
    version      BIGINT         NOT NULL,
    -- Mirrors AccountType: customer accounts have an owner and cannot go negative; funding accounts may.
    CONSTRAINT account_owner_matches_type_ck CHECK (
        (account_type = 'CUSTOMER' AND customer_id IS NOT NULL AND holder_tckn IS NOT NULL AND balance >= 0)
        OR (account_type = 'FUNDING' AND customer_id IS NULL AND holder_tckn IS NULL))
);

CREATE UNIQUE INDEX account_one_funding_per_currency_ux ON account (currency) WHERE account_type = 'FUNDING';
CREATE INDEX account_customer_id_ix ON account (customer_id);

CREATE TABLE ledger_entry (
    id           UUID PRIMARY KEY,
    posting_id   UUID           NOT NULL,
    account_id   UUID           NOT NULL REFERENCES account (id),
    direction    VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount       NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency     VARCHAR(3)     NOT NULL,
    posting_type VARCHAR(32)    NOT NULL,
    posted_at    TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ledger_entry_account_posted_ix ON ledger_entry (account_id, posted_at DESC, id DESC);
CREATE INDEX ledger_entry_posting_ix ON ledger_entry (posting_id);

-- Defence in depth: the domain already refuses unbalanced postings; the database refuses them too. The check is
-- deferred to commit time because the legs of a posting are inserted one row at a time.
CREATE FUNCTION ledger_posting_must_balance() RETURNS trigger AS $$
DECLARE
    currency_count INTEGER;
    signed_total   NUMERIC(19, 2);
BEGIN
    SELECT COUNT(DISTINCT currency),
           COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
      INTO currency_count, signed_total
      FROM ledger_entry
     WHERE posting_id = NEW.posting_id;
    IF currency_count <> 1 OR signed_total <> 0 THEN
        RAISE EXCEPTION 'posting % does not balance (currencies: %, signed total: %)',
            NEW.posting_id, currency_count, signed_total;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_entry_balanced_tg
    AFTER INSERT ON ledger_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_posting_must_balance();

-- The ledger is append-only: corrections are new postings, never edits.
CREATE FUNCTION ledger_entry_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entry is append-only; % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entry_append_only_tg
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION ledger_entry_is_append_only();

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    schema_version INTEGER      NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    partition_key  VARCHAR(64)  NOT NULL,
    payload        JSONB        NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ
);

-- The relay (roadmap week 3) polls only unpublished rows, oldest first.
CREATE INDEX outbox_event_unpublished_ix ON outbox_event (occurred_at) WHERE published_at IS NULL;
