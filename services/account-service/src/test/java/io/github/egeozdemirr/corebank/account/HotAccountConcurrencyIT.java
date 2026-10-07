package io.github.egeozdemirr.corebank.account;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.egeozdemirr.corebank.account.support.PostgresContainerConfiguration;
import io.github.egeozdemirr.corebank.account.support.SyntheticData;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * Many requests in parallel against the same funding account: every opening must succeed and the books must still
 * balance afterwards (ADR-0003).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class HotAccountConcurrencyIT {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String TRY_FUNDING_ACCOUNT = "00000000-0000-4000-8000-000000000001";
    private static final int PARALLEL_CLIENTS = 16;
    private static final int OPENINGS = 200;
    private static final BigDecimal DEPOSIT = new BigDecimal("10.00");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void concurrentOpeningsWithDeposit_allSucceedAndTheBooksBalance() throws Exception {
        BigDecimal fundingBefore = fundingBalance();
        long startedAt = System.nanoTime();

        List<Integer> statuses = runInParallel(OPENINGS, this::openAccountWithDeposit);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        Map<Integer, Long> countByStatus = statuses.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long created = countByStatus.getOrDefault(201, 0L);

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

    private BigDecimal ledgerSignedTotal() {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
                          FROM ledger_entry
                        """)
                .query(BigDecimal.class).single();
    }
}
