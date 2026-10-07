package io.github.egeozdemirr.corebank.transfer.domain.policy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.DailyLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.SingleTransactionLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.UnsupportedCurrencyException;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import java.util.Collection;
import java.util.Currency;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Approval threshold and limits per currency. A currency without a policy cannot be transferred. */
public final class TransferPolicy {

    private final Map<Currency, CurrencyPolicy> policies;

    public TransferPolicy(Collection<CurrencyPolicy> currencyPolicies) {
        this.policies = currencyPolicies.stream().collect(Collectors.toUnmodifiableMap(
                CurrencyPolicy::currency, Function.identity()));
    }

    /** "Above the threshold" is strict: an amount equal to the threshold is approved automatically. */
    public boolean requiresApproval(Money amount) {
        return amount.isGreaterThan(policyFor(amount.currency()).approvalThreshold());
    }

    public void requireWithinSingleTransactionLimit(Money amount) {
        Money limit = policyFor(amount.currency()).singleTransactionLimit();
        if (amount.isGreaterThan(limit)) {
            throw new SingleTransactionLimitExceededException(amount, limit);
        }
    }

    /** The limit itself, for stores that check and reserve the daily total in one atomic step. */
    public Money dailyLimit(Currency currency) {
        return policyFor(currency).dailyLimit();
    }

    /** {@code usedToday}: what the source account already transferred on the business day, in the same currency. */
    public void requireWithinDailyLimit(Money usedToday, Money amount) {
        Money limit = dailyLimit(amount.currency());
        if (usedToday.plus(amount).isGreaterThan(limit)) {
            throw new DailyLimitExceededException(amount, limit);
        }
    }

    private CurrencyPolicy policyFor(Currency currency) {
        CurrencyPolicy policy = policies.get(currency);
        if (policy == null) {
            throw new UnsupportedCurrencyException(currency.getCurrencyCode());
        }
        return policy;
    }
}
