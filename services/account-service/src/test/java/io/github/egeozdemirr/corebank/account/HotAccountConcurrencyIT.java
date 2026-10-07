package io.github.egeozdemirr.corebank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.egeozdemirr.corebank.account.application.LedgerPostingService;
import io.github.egeozdemirr.corebank.account.application.OpenAccountCommand;
import io.github.egeozdemirr.corebank.account.application.OpenAccountService;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.account.CustomerId;
import io.github.egeozdemirr.corebank.account.domain.account.HolderName;
import io.github.egeozdemirr.corebank.account.domain.exception.DomainException;
import io.github.egeozdemirr.corebank.account.domain.identity.Tckn;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.support.PostgresContainerConfiguration;
import io.github.egeozdemirr.corebank.account.support.SyntheticData;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Many requests in parallel against the same rows: every operation must succeed, nothing may deadlock, and the
 * books must balance to the cent afterwards (ADR-0003).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class HotAccountConcurrencyIT {

    private static final Logger LOG = LoggerFactory.getLogger(HotAccountConcurrencyIT.class);

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String TRY_FUNDING_ACCOUNT = "00000000-0000-4000-8000-000000000001";
    private static final int PARALLEL_CLIENTS = 16;
    private static final int OPENINGS = 200;
    private static final BigDecimal DEPOSIT = new BigDecimal("10.00");
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("10000.00");
    private static final int TRANSFERS = 400;
    private static final int MEASURED_TRANSFERS = 200;
    private static final int WARM_UP_TRANSFERS = 50;
    private static final int DISTINCT_AMOUNTS = 7;
    private static final String DEADLOCK_SQL_STATE = "40P01";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private OpenAccountService openAccountService;

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentOpeningsWithDeposit_allSucceedAndTheBooksBalance() throws Exception {
        BigDecimal fundingBefore = fundingBalance();
        long startedAt = System.nanoTime();

        List<Integer> statuses = runInParallel(OPENINGS, this::openAccountWithDeposit);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        Map<Integer, Long> countByStatus = statuses.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long created = countByStatus.getOrDefault(201, 0L);
        LOG.info("Measured: {} openings by {} parallel clients in {} ms, statuses {}",
                OPENINGS, PARALLEL_CLIENTS, elapsed.toMillis(), countByStatus);
        BigDecimal fundingAfter = fundingBalance();
        BigDecimal ledgerTotal = ledgerSignedTotal();
        // Soft assertions: a failing run reports the measured status counts and the book checks together.
        assertSoftly(softly -> {
            softly.assertThat(countByStatus)
                    .as("HTTP status counts for %d openings by %d parallel clients in %d ms",
                            OPENINGS, PARALLEL_CLIENTS, elapsed.toMillis())
                    .containsOnlyKeys(201);
            softly.assertThat(ledgerTotal).as("signed total of the whole ledger").isEqualByComparingTo("0");
            softly.assertThat(fundingAfter)
                    .as("funding balance moved by exactly the committed deposits")
                    .isEqualByComparingTo(fundingBefore.subtract(DEPOSIT.multiply(BigDecimal.valueOf(created))));
        });
    }

    /**
     * Every pair of accounts receives transfers in both directions at the same time (A to B and B to A), which
     * deadlocks if two postings lock the same rows in different orders. PostgreSQL resolves a deadlock by aborting
     * one of the transactions with SQLState 40P01, so "no failure at all" also proves "no deadlock".
     */
    @Test
    void concurrentTransfersInBothDirections_neverDeadlockAndLoseNoUpdate() throws Exception {
        List<AccountId> accounts = openCustomerAccounts(4);
        List<Transfer> transfers = IntStream.range(0, TRANSFERS)
                .mapToObj(index -> crossingTransfer(accounts, index))
                .toList();

        List<Optional<String>> outcomes = runInParallel(transfers.size(), transferRunner(transfers));

        assertThat(outcomes.stream().flatMap(Optional::stream).toList())
                .as("failed transfers (a deadlock would show SQLState %s)", DEADLOCK_SQL_STATE)
                .isEmpty();
        Map<AccountId, BigDecimal> expected = expectedBalances(accounts, transfers);
        BigDecimal customerTotal = accounts.stream().map(this::storedBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ledgerTotal = ledgerSignedTotal();
        assertSoftly(softly -> {
            for (AccountId account : accounts) {
                softly.assertThat(storedBalance(account)).as("stored balance of %s", account)
                        .isEqualByComparingTo(expected.get(account));
                softly.assertThat(ledgerBalance(account)).as("ledger-derived balance of %s", account)
                        .isEqualByComparingTo(expected.get(account));
            }
            softly.assertThat(customerTotal).as("money is conserved between the customer accounts")
                    .isEqualByComparingTo(STARTING_BALANCE.multiply(BigDecimal.valueOf(accounts.size())));
            softly.assertThat(ledgerTotal).as("signed total of the whole ledger").isEqualByComparingTo("0");
        });
    }

    /** Row locks serialise postings on the same account; postings on different accounts run side by side. */
    @Test
    void lockQueueingCost_hotPairVersusDisjointPairs() throws Exception {
        List<AccountId> accounts = openCustomerAccounts(PARALLEL_CLIENTS);
        timed(transfersBetweenPairs(accounts, WARM_UP_TRANSFERS));
        List<Transfer> hotPair = IntStream.range(0, MEASURED_TRANSFERS)
                .mapToObj(index -> transfer(accounts.get(index % 2), accounts.get(1 - index % 2), index))
                .toList();

        long hotPairMillis = timed(hotPair);
        long disjointPairsMillis = timed(transfersBetweenPairs(accounts, MEASURED_TRANSFERS));

        LOG.info("Measured: {} transfers by {} parallel clients, one hot pair {} ms, {} disjoint pairs {} ms",
                MEASURED_TRANSFERS, PARALLEL_CLIENTS, hotPairMillis, accounts.size() / 2, disjointPairsMillis);
        assertThat(ledgerSignedTotal()).isEqualByComparingTo("0");
    }

    private long timed(List<Transfer> transfers) throws Exception {
        long startedAt = System.nanoTime();
        List<Optional<String>> outcomes = runInParallel(transfers.size(), transferRunner(transfers));
        long millis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        assertThat(outcomes.stream().flatMap(Optional::stream).toList()).isEmpty();
        return millis;
    }

    private int openAccountWithDeposit() {
        String body = """
                {"customerId": "%s", "holderName": "%s", "holderTckn": "%s", "currency": "TRY",
                 "openingDeposit": "%s"}
                """.formatted(UUID.randomUUID(), SyntheticData.fullName(), SyntheticData.tckn(),
                DEPOSIT.toPlainString());
        return mvc.post().uri(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange()
                .getResponse()
                .getStatus();
    }

    private List<AccountId> openCustomerAccounts(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> openAccountService.open(new OpenAccountCommand(CustomerId.newId(),
                        new HolderName(SyntheticData.fullName()), new Tckn(SyntheticData.tckn()), TestAccounts.TRY,
                        TestAccounts.money(STARTING_BALANCE.toPlainString()))).id())
                .toList();
    }

    /** Four pairs, A-B, C-D, A-C and B-D, each used in both directions in turn. */
    private static Transfer crossingTransfer(List<AccountId> accounts, int index) {
        int[][] pairs = {{0, 1}, {2, 3}, {0, 2}, {1, 3}};
        int[] pair = pairs[index % pairs.length];
        boolean forward = index / pairs.length % 2 == 0;
        AccountId first = accounts.get(pair[0]);
        AccountId second = accounts.get(pair[1]);
        return forward ? transfer(first, second, index) : transfer(second, first, index);
    }

    private static List<Transfer> transfersBetweenPairs(List<AccountId> accounts, int count) {
        int pairCount = accounts.size() / 2;
        return IntStream.range(0, count)
                .mapToObj(index -> {
                    int pair = index % pairCount;
                    boolean forward = index / pairCount % 2 == 0;
                    AccountId first = accounts.get(2 * pair);
                    AccountId second = accounts.get(2 * pair + 1);
                    return forward ? transfer(first, second, index) : transfer(second, first, index);
                })
                .toList();
    }

    private static Transfer transfer(AccountId from, AccountId to, int index) {
        return new Transfer(from, to, BigDecimal.valueOf(index % DISTINCT_AMOUNTS + 1L).setScale(2));
    }

    /** Each call takes the next transfer of the list, so every transfer runs exactly once. */
    private Callable<Optional<String>> transferRunner(List<Transfer> transfers) {
        AtomicInteger next = new AtomicInteger();
        return () -> post(transfers.get(next.getAndIncrement()));
    }

    private Optional<String> post(Transfer transfer) {
        try {
            transactionTemplate.executeWithoutResult(status -> ledgerPostingService.post(Posting.between(
                    PostingId.newId(), PostingType.TRANSFER, transfer.from(), transfer.to(),
                    TestAccounts.money(transfer.amount().toPlainString()), Instant.now())));
            return Optional.empty();
        } catch (DataAccessException | TransactionException | DomainException exception) {
            // Deadlocks, lock timeouts and version conflicts are DataAccessExceptions; anything else fails the test.
            return Optional.of(describe(exception));
        }
    }

    private static String describe(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return exception.getClass().getSimpleName() + " SQLState " + sqlException.getSQLState();
            }
        }
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private static Map<AccountId, BigDecimal> expectedBalances(List<AccountId> accounts, List<Transfer> transfers) {
        Map<AccountId, BigDecimal> balances = new HashMap<>();
        accounts.forEach(account -> balances.put(account, STARTING_BALANCE));
        for (Transfer transfer : transfers) {
            balances.merge(transfer.from(), transfer.amount().negate(), BigDecimal::add);
            balances.merge(transfer.to(), transfer.amount(), BigDecimal::add);
        }
        return balances;
    }

    /** All clients wait on one latch so that the requests really overlap. */
    private static <T> List<T> runInParallel(int calls, Callable<T> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try (ExecutorService clients = Executors.newFixedThreadPool(PARALLEL_CLIENTS)) {
            for (int i = 0; i < calls; i++) {
                futures.add(clients.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private BigDecimal fundingBalance() {
        byte[] body = mvc.get().uri(ACCOUNTS + "/{id}/balance", TRY_FUNDING_ACCOUNT).exchange()
                .getResponse().getContentAsByteArray();
        return new BigDecimal(jsonMapper.readTree(body).get("balance").get("amount").asString());
    }

    private BigDecimal storedBalance(AccountId accountId) {
        return jdbcClient.sql("SELECT balance FROM account WHERE id = :id")
                .param("id", accountId.value()).query(BigDecimal.class).single();
    }

    private BigDecimal ledgerBalance(AccountId accountId) {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
                          FROM ledger_entry
                         WHERE account_id = :id
                        """)
                .param("id", accountId.value()).query(BigDecimal.class).single();
    }

    private BigDecimal ledgerSignedTotal() {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
                          FROM ledger_entry
                        """)
                .query(BigDecimal.class).single();
    }

    private record Transfer(AccountId from, AccountId to, BigDecimal amount) {
    }
}
