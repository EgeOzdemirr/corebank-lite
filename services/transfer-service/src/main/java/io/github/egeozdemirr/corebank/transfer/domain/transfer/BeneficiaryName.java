package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidBeneficiaryNameException;
import java.util.Objects;

/** Recipient name as entered by the maker; screened against sanctions lists downstream (aml-monitor). */
public record BeneficiaryName(String value) {

    public static final int MAX_LENGTH = 140;

    public BeneficiaryName {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (value.isEmpty()) {
            throw new InvalidBeneficiaryNameException("Beneficiary name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidBeneficiaryNameException("Beneficiary name must be at most " + MAX_LENGTH + " characters");
        }
    }
}
