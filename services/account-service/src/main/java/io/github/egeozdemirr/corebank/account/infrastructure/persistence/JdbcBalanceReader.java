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

/** Balance is the hottest read; a single-row JDBC query avoids loading and mapping the whole aggregate. */
@Repository
class JdbcBalanceReader implements BalanceReader {

    private static final String SELECT_BALANCE = "SELECT iban, balance, currency FROM account WHERE id = :id";

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
