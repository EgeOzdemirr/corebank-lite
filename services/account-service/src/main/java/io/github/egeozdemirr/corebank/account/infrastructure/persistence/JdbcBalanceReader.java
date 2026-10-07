package io.github.egeozdemirr.corebank.account.infrastructure.persistence;

import io.github.egeozdemirr.corebank.account.application.AccountBalance;
import io.github.egeozdemirr.corebank.account.application.port.BalanceReader;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import io.github.egeozdemirr.corebank.account.domain.money.Currencies;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Balance is the hottest read; a single-row JDBC query avoids loading and mapping the whole aggregate. Accounts
 * without a materialised balance (funding accounts, ADR-0003) get the signed sum of their ledger lines, which the
 * covering index {@code ledger_entry_account_amount_ix} serves without touching the table.
 */
@Repository
class JdbcBalanceReader implements BalanceReader {

    private static final String SELECT_BALANCE = """
            SELECT a.iban, a.currency,
                   COALESCE(a.balance,
                            (SELECT COALESCE(SUM(CASE l.direction WHEN 'CREDIT' THEN l.amount ELSE -l.amount END), 0)
                               FROM ledger_entry l
                              WHERE l.account_id = a.id)) AS balance
              FROM account a
             WHERE a.id = :id
            """;

    private final JdbcClient jdbcClient;

    JdbcBalanceReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<AccountBalance> findBalance(AccountId accountId) {
        return jdbcClient.sql(SELECT_BALANCE)
                .param("id", accountId.value())
                .query((row, rowNumber) -> new AccountBalance(
                        accountId,
                        new Iban(row.getString("iban")),
                        Money.of(row.getBigDecimal("balance"), Currencies.fromCode(row.getString("currency")))))
                .optional();
    }
}
