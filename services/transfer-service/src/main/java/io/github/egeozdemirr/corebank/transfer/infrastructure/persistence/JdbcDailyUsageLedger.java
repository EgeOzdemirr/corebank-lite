package io.github.egeozdemirr.corebank.transfer.infrastructure.persistence;

import io.github.egeozdemirr.corebank.transfer.application.port.DailyUsageLedger;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDailyUsageLedger implements DailyUsageLedger {

    /**
     * Check and increment in one statement: the first transfer of the day inserts the row (if it fits at all), later
     * ones update it only if the new total stays within the limit. ON CONFLICT locks the row, so concurrent
     * reservations for the same account and day are applied one after the other against the current total.
     */
    private static final String RESERVE = """
            INSERT INTO daily_transfer_usage (source_account_id, business_day, currency, reserved)
            SELECT :account, :day, :currency, :amount
             WHERE :amount <= :limit
            ON CONFLICT (source_account_id, business_day, currency)
            DO UPDATE SET reserved = daily_transfer_usage.reserved + EXCLUDED.reserved
             WHERE daily_transfer_usage.reserved + EXCLUDED.reserved <= :limit
            """;

    /** The day and amount come from the transfer row, so the release always hits the day of the reservation. */
    private static final String RELEASE = """
            UPDATE daily_transfer_usage usage
               SET reserved = usage.reserved - transfer.amount
              FROM transfer
             WHERE transfer.id = :transferId
               AND usage.source_account_id = transfer.source_account_id
               AND usage.business_day = transfer.business_day
               AND usage.currency = transfer.currency
            """;

    private static final int ONE_ROW = 1;

    private final JdbcClient jdbcClient;

    JdbcDailyUsageLedger(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean reserve(AccountId sourceAccount, LocalDate businessDay, Money amount, Money dailyLimit) {
        int changedRows = jdbcClient.sql(RESERVE)
                .param("account", sourceAccount.value())
                .param("day", businessDay)
                .param("currency", amount.currency().getCurrencyCode())
                .param("amount", amount.amount())
                .param("limit", dailyLimit.amount())
                .update();
        return changedRows == ONE_ROW;
    }

    @Override
    public void release(TransferId transferId) {
        int changedRows = jdbcClient.sql(RELEASE).param("transferId", transferId.value()).update();
        if (changedRows != ONE_ROW) {
            throw new IllegalStateException("No daily reservation found for transfer " + transferId);
        }
    }
}
