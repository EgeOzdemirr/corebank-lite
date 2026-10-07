package io.github.egeozdemirr.corebank.transfer.support;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.policy.BusinessCalendar;
import io.github.egeozdemirr.corebank.transfer.domain.policy.CurrencyPolicy;
import io.github.egeozdemirr.corebank.transfer.domain.policy.TransferPolicy;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.BeneficiaryName;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferChannel;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** Synthetic test data: IBANs use the unassigned bank code 99999 and valid check digits. */
public final class TestTransfers {

    public static final Currency TRY = Currency.getInstance("TRY");
    public static final Currency USD = Currency.getInstance("USD");
    public static final String SOURCE_IBAN = "TR809999900000000000000001";
    public static final String TARGET_IBAN = "TR539999900000000000000002";
    public static final Instant NOW = Instant.parse("2026-10-07T09:30:00Z");
    public static final UserId MAKER = new UserId("maker-1");
    public static final UserId CHECKER = new UserId("checker-1");
    public static final BusinessCalendar ISTANBUL = new BusinessCalendar(ZoneId.of("Europe/Istanbul"));
    /** 21:00 UTC is midnight in Istanbul: the first instant of the business day after {@link #NOW}. */
    public static final Instant NEXT_BUSINESS_DAY = Instant.parse("2026-10-07T21:00:00Z");

    /** TRY: approval above 50,000.00, at most 100,000.00 per transfer and 250,000.00 per day. */
    public static final TransferPolicy POLICY = new TransferPolicy(List.of(
            new CurrencyPolicy(money("50000.00"), money("100000.00"), money("250000.00"))));

    private TestTransfers() {
    }

    public static Money money(String amount) {
        return Money.of(amount, TRY);
    }

    public static AccountReference account(String iban, Currency currency) {
        return new AccountReference(new AccountId(UUID.randomUUID()), new Iban(iban), currency);
    }

    public static TransferOrder order(String amount) {
        return new TransferOrder(account(SOURCE_IBAN, TRY), account(TARGET_IBAN, TRY),
                new BeneficiaryName("Mehmet Demir"), money(amount), TransferChannel.INTERNET_BANKING, MAKER);
    }
}
