package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;

/** Append-only: ledger entries are never updated or deleted, corrections are new postings. */
public interface LedgerWriter {

    void append(Posting posting);
}
