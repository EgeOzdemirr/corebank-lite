package io.github.egeozdemirr.corebank.transfer.domain.exception;

/**
 * Coarse classification of a domain failure. The API layer maps each category to one HTTP status, so a new
 * exception only has to pick a category and never requires a change to the global error handler.
 */
public enum ErrorCategory {
    INVALID_INPUT,
    NOT_FOUND,
    RULE_VIOLATION,
    /** The request contradicts the current state, for example approving a transfer that is already posted. */
    CONFLICT
}
