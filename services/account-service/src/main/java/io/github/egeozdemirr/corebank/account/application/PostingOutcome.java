package io.github.egeozdemirr.corebank.account.application;

/** Result of {@link LedgerPostingService#post}: either this call recorded the posting, or one with its id existed. */
public enum PostingOutcome {
    POSTED,
    ALREADY_RECORDED
}
