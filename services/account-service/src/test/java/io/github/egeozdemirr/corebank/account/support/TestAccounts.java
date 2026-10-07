package io.github.egeozdemirr.corebank.account.support;

import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.AccountStatus;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerOwner;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.account.InstitutionOwner;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.concurrent.atomic.AtomicLong;

public final class TestAccounts {

    public static final Currency TRY = Currency.getInstance("TRY");
    public static final Currency USD = Currency.getInstance("USD");
    public static final String SYNTHETIC_BANK_CODE = "99999";
    public static final Instant OPENED_AT = Instant.parse("2026-10-06T09:00:00Z");

    private static final AtomicLong ACCOUNT_NUMBERS = new AtomicLong(1000);

    private TestAccounts() {
    }

    public static Iban nextIban() {
        return Iban.forTurkishAccount(SYNTHETIC_BANK_CODE, ACCOUNT_NUMBERS.incrementAndGet());
    }

    public static CustomerOwner customerOwner() {
        return new CustomerOwner(CustomerId.newId(), new HolderName(SyntheticData.fullName()),
                new Tckn(SyntheticData.tckn()));
    }

    public static Account customerAccount(String balance) {
        return Account.restore(AccountId.newId(), nextIban(), customerOwner(), OPENED_AT, AccountStatus.ACTIVE,
                Money.of(balance, TRY));
    }

    public static Account closedCustomerAccount() {
        return Account.restore(AccountId.newId(), nextIban(), customerOwner(), OPENED_AT, AccountStatus.CLOSED,
                Money.zero(TRY));
    }

    public static Account fundingAccount(Currency currency) {
        return Account.restoreWithLedgerBalance(AccountId.newId(), nextIban(),
                new InstitutionOwner(new HolderName("Funding " + currency.getCurrencyCode())), currency, OPENED_AT,
                AccountStatus.ACTIVE);
    }

    public static Money money(String amount) {
        return Money.of(amount, TRY);
    }
}
