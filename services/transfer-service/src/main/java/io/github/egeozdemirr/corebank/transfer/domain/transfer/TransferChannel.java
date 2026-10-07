package io.github.egeozdemirr.corebank.transfer.domain.transfer;

/** Channel through which a transfer was initiated; the same values as the transfer event contracts. */
public enum TransferChannel {
    BRANCH,
    INTERNET_BANKING,
    MOBILE,
    OPEN_BANKING,
    API
}
