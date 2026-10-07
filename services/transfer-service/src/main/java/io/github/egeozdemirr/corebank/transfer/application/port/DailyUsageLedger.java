package io.github.egeozdemirr.corebank.transfer.application.port;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.time.LocalDate;

/**
 * What each source account transferred per business day. Every transfer that is not FAILED counts (REVERSED included,
 * so a reversal cannot win back limit); the amount of a transfer that fails is given back.
 */
public interface DailyUsageLedger {

    /**
     * Adds {@code amount} to the day's total if the result stays within {@code dailyLimit}, in one atomic step, so
     * concurrent requests can never overshoot the limit together. Returns false, and changes nothing, otherwise.
     */
    boolean reserve(AccountId sourceAccount, LocalDate businessDay, Money amount, Money dailyLimit);

    /** Gives a failed transfer's amount back to the day it was reserved on (stored with the transfer). */
    void release(TransferId transferId);
}
