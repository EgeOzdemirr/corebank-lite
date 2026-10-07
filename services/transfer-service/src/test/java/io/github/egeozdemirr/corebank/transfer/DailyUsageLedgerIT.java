package io.github.egeozdemirr.corebank.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.ISTANBUL;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NOW;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.POLICY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.money;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.application.port.DailyUsageLedger;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferWriter;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.support.PostgresContainerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The daily limit is checked and reserved in one statement, so concurrency cannot push the total over the limit. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class DailyUsageLedgerIT {

    private static final LocalDate TODAY = ISTANBUL.businessDayOf(NOW);
    private static final Money LIMIT = money("200.00");
    private static final int PARALLEL_CLIENTS = 16;
    private static final int RESERVATIONS = 60;

    @Autowired
    private DailyUsageLedger dailyUsageLedger;

    @Autowired
    private TransferWriter transferWriter;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void reservations_upToTheLimitSucceedAndBeyondItChangeNothing() {
        AccountId account = new AccountId(UUID.randomUUID());

        assertThat(reserve(account, "150.00")).isTrue();
        assertThat(reserve(account, "50.01")).isFalse();
        assertThat(reserve(account, "50.00")).isTrue();
        assertThat(reserve(account, "0.01")).isFalse();
        assertThat(reservedOn(account, TODAY)).isEqualByComparingTo("200.00");
    }

    @Test
    void firstReservationAboveTheLimit_isRefusedToo() {
        AccountId account = new AccountId(UUID.randomUUID());

        assertThat(reserve(account, "200.01")).isFalse();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM daily_transfer_usage WHERE source_account_id = :id")
                .param("id", account.value()).query(Long.class).single()).isZero();
    }

    @Test
    void eachBusinessDayAndAccount_hasItsOwnTotal() {
        AccountId account = new AccountId(UUID.randomUUID());
        AccountId otherAccount = new AccountId(UUID.randomUUID());

        assertThat(reserve(account, "200.00")).isTrue();
        assertThat(reserve(otherAccount, "200.00")).isTrue();
        Boolean nextDay = transactionTemplate.execute(status ->
                dailyUsageLedger.reserve(account, TODAY.plusDays(1), money("200.00"), LIMIT));
        assertThat(nextDay).isTrue();
    }

    @Test
    void release_givesTheAmountBackToTheDayOfTheReservation() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("120.00"), POLICY, NOW);
        AccountId source = transfer.order().source().accountId();
        LocalDate yesterday = TODAY.minusDays(1);
        transactionTemplate.executeWithoutResult(status -> {
            dailyUsageLedger.reserve(source, yesterday, transfer.order().amount(), LIMIT);
            transferWriter.add(transfer, yesterday);
        });
        transactionTemplate.executeWithoutResult(status ->
                dailyUsageLedger.reserve(source, TODAY, money("30.00"), LIMIT));

        transactionTemplate.executeWithoutResult(status -> dailyUsageLedger.release(transfer.id()));

        assertThat(reservedOn(source, yesterday)).isEqualByComparingTo("0.00");
        assertThat(reservedOn(source, TODAY)).isEqualByComparingTo("30.00");
    }

    @Test
    void releaseWithoutReservation_isAProgrammingError() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                dailyUsageLedger.release(TransferId.newId())))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No daily reservation");
    }

    @Test
    void concurrentReservations_neverOvershootTheLimit() throws Exception {
        AccountId account = new AccountId(UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try (ExecutorService clients = Executors.newFixedThreadPool(PARALLEL_CLIENTS)) {
            for (int i = 0; i < RESERVATIONS; i++) {
                results.add(clients.submit(() -> {
                    start.await();
                    return reserve(account, "10.00");
                }));
            }
            start.countDown();
            long granted = 0;
            for (Future<Boolean> result : results) {
                granted += result.get() ? 1 : 0;
            }
            assertThat(granted).isEqualTo(20);
        }
        assertThat(reservedOn(account, TODAY)).isEqualByComparingTo("200.00");
    }

    private boolean reserve(AccountId account, String amount) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                dailyUsageLedger.reserve(account, TODAY, money(amount), LIMIT)));
    }

    private BigDecimal reservedOn(AccountId account, LocalDate day) {
        return jdbcClient.sql("SELECT reserved FROM daily_transfer_usage WHERE source_account_id = :id "
                        + "AND business_day = :day")
                .param("id", account.value()).param("day", day).query(BigDecimal.class).single();
    }
}
