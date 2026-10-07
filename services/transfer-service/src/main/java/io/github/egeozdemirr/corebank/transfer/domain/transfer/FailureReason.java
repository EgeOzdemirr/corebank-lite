package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Why a transfer failed: the stable error code of the definite rejection (for example INSUFFICIENT_FUNDS). No free
 * text, so no personal data can end up in the TransferFailed event.
 */
public record FailureReason(String code) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]*$");

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
