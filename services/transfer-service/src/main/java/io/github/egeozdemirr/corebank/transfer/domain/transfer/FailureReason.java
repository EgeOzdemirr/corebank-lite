package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Why a transfer failed: a stable code, either a definite rejection by account-service (for example
 * INSUFFICIENT_FUNDS) or one of the constants below. No free text, so no personal data can end up in TransferFailed.
 */
public record FailureReason(String code) {

    // Declared first: the constants below are built with it during class initialisation.
    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    /** A checker other than the maker turned the transfer down. */
    public static final FailureReason REJECTED_BY_CHECKER = new FailureReason("REJECTED_BY_CHECKER");

    /** Nobody approved the transfer within the business day of its request. */
    public static final FailureReason APPROVAL_EXPIRED = new FailureReason("APPROVAL_EXPIRED");

    public FailureReason {
        Objects.requireNonNull(code, "code");
        if (!FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("Failure code must be an upper-case code such as INSUFFICIENT_FUNDS");
        }
    }

    @Override
    public String toString() {
        return code;
    }
}
