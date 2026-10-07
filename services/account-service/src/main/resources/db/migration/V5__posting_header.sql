-- One row per posting (ADR-0004). Its primary key is what makes PostTransfer idempotent: a caller that retries with
-- the same posting id after a timeout finds the id taken instead of moving the money twice.
CREATE TABLE posting (
    id           UUID PRIMARY KEY,
    posting_type VARCHAR(32) NOT NULL,
    posted_at    TIMESTAMPTZ NOT NULL
);

-- Every existing posting gets its header; all lines of one posting share type and time.
INSERT INTO posting (id, posting_type, posted_at)
SELECT posting_id, MIN(posting_type), MIN(posted_at)
  FROM ledger_entry
 GROUP BY posting_id;

ALTER TABLE ledger_entry
    ADD CONSTRAINT ledger_entry_posting_fk FOREIGN KEY (posting_id) REFERENCES posting (id);

-- A header without lines would claim an id without moving money; checked at commit like the balance (ADR-0002).
CREATE FUNCTION posting_must_have_entries() RETURNS trigger AS $$
BEGIN
    IF (SELECT COUNT(*) FROM ledger_entry WHERE posting_id = NEW.id) < 2 THEN
        RAISE EXCEPTION 'posting % has fewer than two ledger entries', NEW.id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER posting_has_entries_tg
    AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION posting_must_have_entries();

-- Postings are append-only like their lines.
CREATE FUNCTION table_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER posting_append_only_tg
    BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION table_is_append_only();

CREATE TRIGGER posting_no_truncate_tg
    BEFORE TRUNCATE ON posting
    FOR EACH STATEMENT EXECUTE FUNCTION table_is_append_only();
