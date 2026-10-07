package io.github.egeozdemirr.corebank.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Concurrent decisions on one transfer run one after the other under the row lock: exactly one of them wins. */
class ConcurrentDecisionIT extends TransferApiIntegrationTest {

    private static final int PARALLEL_CLIENTS = 16;
    private static final int TRANSFERS = 30;

    @RepeatedTest(5)
    void approvalAndRejectionAtTheSameTime_onlyOneWinsAndTheOtherGets409() throws Exception {
        String transferId = transferIdOf(create("60000.00"));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService checkers = Executors.newFixedThreadPool(2)) {
            Future<MvcTestResult> approval = checkers.submit(() -> {
                start.await();
                return approve(transferId, CHECKER);
            });
            Future<MvcTestResult> rejection = checkers.submit(() -> {
                start.await();
                return reject(transferId, "checker-2");
            });
            start.countDown();
            int approvalStatus = approval.get().getResponse().getStatus();
            int rejectionStatus = rejection.get().getResponse().getStatus();

            assertThat(List.of(approvalStatus, rejectionStatus)).containsExactlyInAnyOrder(200, 409);
            String expectedStatus = approvalStatus == 200 ? "POSTED" : "FAILED";
            assertThat(storedStatus(transferId)).isEqualTo(expectedStatus);
            assertThat(outboxEventsOf(transferId, "TransferApproved") + outboxEventsOf(transferId, "TransferFailed"))
                    .isOne();
            assertThat(reservedToday()).isEqualByComparingTo(approvalStatus == 200 ? "60000.00" : "0.00");
        }
    }

    /** 30 transfers of 10,000.00 against a daily limit of 250,000.00: exactly 25 fit, whatever the timing. */
    @Test
    void parallelTransfersFromOneAccount_neverExceedTheDailyLimit() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        Callable<Integer> transfer = () -> {
            start.await();
            return create("10000.00").getResponse().getStatus();
        };

        try (ExecutorService clients = Executors.newFixedThreadPool(PARALLEL_CLIENTS)) {
            for (int i = 0; i < TRANSFERS; i++) {
                statuses.add(clients.submit(transfer));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                results.add(status.get());
            }
            assertThat(results).filteredOn(status -> status == 201).hasSize(25);
            assertThat(results).filteredOn(status -> status == 422).hasSize(5);
        }
        assertThat(reservedToday()).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(transfersOfSource()).isEqualTo(25);
    }
}
