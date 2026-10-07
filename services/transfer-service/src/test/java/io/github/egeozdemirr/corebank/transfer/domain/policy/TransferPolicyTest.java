package io.github.egeozdemirr.corebank.transfer.domain.policy;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.POLICY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TRY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.USD;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.domain.exception.DailyLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.SingleTransactionLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.UnsupportedCurrencyException;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import org.junit.jupiter.api.Test;

/** Policy of the tests: TRY approval above 50,000.00, single limit 100,000.00, daily limit 250,000.00. */
class TransferPolicyTest {

    @Test
    void approval_isRequiredStrictlyAboveTheThreshold() {
        assertThat(POLICY.requiresApproval(money("50000.00"))).isFalse();
        assertThat(POLICY.requiresApproval(money("50000.01"))).isTrue();
    }

    @Test
    void singleTransactionLimit_allowsTheLimitItself() {
        assertThatCode(() -> POLICY.requireWithinSingleTransactionLimit(money("100000.00")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> POLICY.requireWithinSingleTransactionLimit(money("100000.01")))
                .isInstanceOf(SingleTransactionLimitExceededException.class)
                .hasMessageContaining("100000.00 TRY");
    }

    @Test
    void dailyLimit_countsWhatTheAccountAlreadyTransferredToday() {
        assertThat(POLICY.dailyLimit(TRY)).isEqualTo(money("250000.00"));
        assertThatCode(() -> POLICY.requireWithinDailyLimit(money("150000.00"), money("100000.00")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> POLICY.requireWithinDailyLimit(money("150000.01"), money("100000.00")))
                .isInstanceOf(DailyLimitExceededException.class);
    }

    @Test
    void currencyWithoutPolicy_isUnsupportedEverywhere() {
        Money dollars = Money.of("1.00", USD);

        assertThatThrownBy(() -> POLICY.requiresApproval(dollars)).isInstanceOf(UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> POLICY.requireWithinSingleTransactionLimit(dollars))
                .isInstanceOf(UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> POLICY.dailyLimit(USD)).isInstanceOf(UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> POLICY.requireWithinDailyLimit(Money.zero(USD), dollars))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    @Test
    void inconsistentConfiguration_isRejectedAtStartup() {
        assertThatThrownBy(() -> new CurrencyPolicy(money("0.00"), money("1.00"), money("1.00")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> new CurrencyPolicy(money("2.00"), money("1.00"), money("3.00")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("single transaction limit");
        assertThatThrownBy(() -> new CurrencyPolicy(money("1.00"), money("3.00"), money("2.00")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("daily limit");
        assertThatThrownBy(() -> new CurrencyPolicy(money("1.00"), Money.of("2.00", USD), money("3.00")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("one currency");
        assertThatThrownBy(() -> new CurrencyPolicy(money("1.00"), money("2.00"), Money.of("3.00", USD)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("one currency");
    }

    @Test
    void equalThresholdAndLimits_areAllowed() {
        CurrencyPolicy flat = new CurrencyPolicy(money("10.00"), money("10.00"), money("10.00"));

        assertThat(flat.currency()).isEqualTo(TRY);
    }
}
