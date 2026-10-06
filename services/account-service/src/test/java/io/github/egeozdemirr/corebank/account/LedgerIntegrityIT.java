package io.github.egeozdemirr.corebank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.account.support.PostgresContainerConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The database enforces the ledger rules on its own, even if application code is bypassed. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class LedgerIntegrityIT {

    private static final UUID TRY_FUNDING = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID USD_FUNDING = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID EUR_FUNDING = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void flyway_seedsOneFundingAccountPerSupportedCurrency() {
        assertThat(jdbcClient.sql("SELECT currency FROM account WHERE account_type = 'FUNDING' ORDER BY currency")
                .query(String.class).list()).containsExactly("EUR", "TRY", "USD");
    }

    @Test
    void balancedPosting_isAccepted() {
        UUID postingId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            insertEntry(postingId, TRY_FUNDING, "DEBIT", "5.00", "TRY");
            insertEntry(postingId, USD_FUNDING, "CREDIT", "5.00", "TRY");
        });

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM ledger_entry WHERE posting_id = :id")
                .param("id", postingId).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void unbalancedPosting_isRejectedAtCommit() {
        UUID postingId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            insertEntry(postingId, TRY_FUNDING, "DEBIT", "10.00", "TRY");
            insertEntry(postingId, USD_FUNDING, "CREDIT", "9.99", "TRY");
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("does not balance");
    }

    @Test
    void postingMixingCurrencies_isRejectedAtCommit() {
        UUID postingId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            insertEntry(postingId, TRY_FUNDING, "DEBIT", "10.00", "TRY");
            insertEntry(postingId, EUR_FUNDING, "CREDIT", "10.00", "EUR");
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("does not balance");
    }

    @Test
    void ledgerEntries_cannotBeUpdatedOrDeleted() {
        UUID postingId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            insertEntry(postingId, TRY_FUNDING, "DEBIT", "1.00", "TRY");
            insertEntry(postingId, USD_FUNDING, "CREDIT", "1.00", "TRY");
        });

        assertThatThrownBy(() -> jdbcClient.sql("UPDATE ledger_entry SET amount = 2 WHERE posting_id = :id")
                .param("id", postingId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcClient.sql("DELETE FROM ledger_entry WHERE posting_id = :id")
                .param("id", postingId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void ledger_cannotBeTruncated() {
        assertThatThrownBy(() -> jdbcClient.sql("TRUNCATE ledger_entry").update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void ledger_cannotBeTruncatedThroughCascadeFromAccount() {
        assertThatThrownBy(() -> jdbcClient.sql("TRUNCATE account CASCADE").update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void customerAccount_cannotHaveNegativeBalanceEvenViaSql() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                        INSERT INTO account (id, iban, account_type, customer_id, holder_name, holder_tckn, currency,
                                             balance, status, opened_at, version)
                        VALUES (:id, 'TR809999900000000000000999', 'CUSTOMER', :customer, 'Test', '10000000146',
                                'TRY', -1, 'ACTIVE', now(), 0)
                        """)
                .param("id", UUID.randomUUID())
                .param("customer", UUID.randomUUID())
                .update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("account_owner_matches_type_ck");
    }

    private void insertEntry(UUID postingId, UUID accountId, String direction, String amount, String currency) {
        jdbcClient.sql("""
                        INSERT INTO ledger_entry (id, posting_id, account_id, direction, amount, currency,
                                                  posting_type, posted_at)
                        VALUES (:id, :posting, :account, :direction, CAST(:amount AS NUMERIC), :currency,
                                'OPENING_DEPOSIT', :postedAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("posting", postingId)
                .param("account", accountId)
                .param("direction", direction)
                .param("amount", amount)
                .param("currency", currency)
                .param("postedAt", Timestamp.from(Instant.now()))
                .update();
    }
}
