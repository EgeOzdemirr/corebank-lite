package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;

/** Append-only: ledger entries are never updated or deleted, corrections are new postings. */
public interface LedgerWriter {

    /**
     * Records the posting's id, type and time before anything else is written. Returns false if a posting with this
     * id exists; if another transaction is recording the same id, waits for it to finish and then decides.
     */
    boolean claim(Posting posting);

    /** Appends the entries of a posting claimed earlier in the same transaction. */
    void append(Posting posting);
}
