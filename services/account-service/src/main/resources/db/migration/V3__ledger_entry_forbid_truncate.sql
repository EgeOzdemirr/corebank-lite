-- Row-level UPDATE/DELETE triggers (V1) do not fire for TRUNCATE, which would empty the ledger in one statement. A
-- statement-level trigger closes that gap; it also fires when the ledger is reached through TRUNCATE ... CASCADE.
CREATE TRIGGER ledger_entry_no_truncate_tg
    BEFORE TRUNCATE ON ledger_entry
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_entry_is_append_only();
