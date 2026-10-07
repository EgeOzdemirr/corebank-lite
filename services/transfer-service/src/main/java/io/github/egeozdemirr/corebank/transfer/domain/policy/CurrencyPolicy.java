package io.github.egeozdemirr.corebank.transfer.domain.policy;

import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import java.util.Currency;
import java.util.Objects;

/**
 * Transfer rules of one currency. A transfer above {@code approvalThreshold} needs a checker; no single transfer may
 * exceed {@code singleTransactionLimit}; the transfers of one source account on one business day may not exceed
 * {@code dailyLimit}. The values come from configuration; inconsistent values stop the service at startup.
 */
public record CurrencyPolicy(Money approvalThreshold, Money singleTransactionLimit, Money dailyLimit) {

    public CurrencyPolicy {
        Objects.requireNonNull(approvalThreshold, "approvalThreshold");
        Objects.requireNonNull(singleTransactionLimit, "singleTransactionLimit");
        Objects.requireNonNull(dailyLimit, "dailyLimit");
        requireSameCurrency(approvalThreshold, singleTransactionLimit, dailyLimit);
        if (!approvalThreshold.isPositive()) {
            throw new IllegalArgumentException("Approval threshold must be positive but was " + approvalThreshold);
        }
        if (approvalThreshold.isGreaterThan(singleTransactionLimit)) {
            throw new IllegalArgumentException("Approval threshold " + approvalThreshold
                    + " is above the single transaction limit " + singleTransactionLimit);
        }
        if (singleTransactionLimit.isGreaterThan(dailyLimit)) {
            throw new IllegalArgumentException("Single transaction limit " + singleTransactionLimit
                    + " is above the daily limit " + dailyLimit);
        }
    }

    public Currency currency() {
        return dailyLimit.currency();
    }

    private static void requireSameCurrency(Money first, Money second, Money third) {
        if (!second.hasCurrency(first.currency()) || !third.hasCurrency(first.currency())) {
            throw new IllegalArgumentException("Threshold and limits must share one currency: " + first + ", "
                    + second + ", " + third);
        }
    }
}
