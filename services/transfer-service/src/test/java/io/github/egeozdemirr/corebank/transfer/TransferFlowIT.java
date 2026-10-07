package io.github.egeozdemirr.corebank.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService.failWith;
import static io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService.posted;
import static io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService.slow;
import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The transfer lifecycle end to end, including every way a posting can end. */
class TransferFlowIT extends TransferApiIntegrationTest {

    @Test
    void transferUpToTheThreshold_isPostedAtOnceWithTheTransferIdAsPostingId() {
        MvcTestResult result = create("50000.00");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String transferId = transferIdOf(result);
        assertThat(json(result).get("status").asString()).isEqualTo("POSTED");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(TRANSFERS + "/" + transferId);
        assertThat(storedStatus(transferId)).isEqualTo("POSTED");
        assertThat(accountService.postingRequests()).singleElement()
                .satisfies(request -> assertThat(request.getPostingId()).isEqualTo(transferId));
        assertThat(outboxEventsOf(transferId, "TransferRequested")).isOne();
        assertThat(outboxEventsOf(transferId, "TransferApproved")).isOne();
        assertThat(outboxEventsOf(transferId, "TransferPosted")).isOne();
        assertThat(reservedToday()).isEqualByComparingTo("50000.00");
    }

    @Test
    void transferAboveTheThreshold_isPostedOnceAnotherUserApprovesIt() {
        MvcTestResult created = create("50000.01");
        String transferId = transferIdOf(created);
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        assertThat(json(created).get("status").asString()).isEqualTo("PENDING_APPROVAL");
        assertThat(accountService.postingRequests()).isEmpty();

        MvcTestResult approved = approve(transferId, CHECKER);

        assertThat(approved).hasStatus(HttpStatus.OK);
        assertThat(json(approved).get("status").asString()).isEqualTo("POSTED");
        assertThat(json(approved).get("checkerUserId").asString()).isEqualTo(CHECKER);
        assertThat(outboxEventsOf(transferId, "TransferPosted")).isOne();
    }

    @Test
    void makerCannotApproveOrRejectTheirOwnTransfer() {
        String transferId = transferIdOf(create("60000.00"));

        assertThat(approve(transferId, MAKER)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("MAKER_CANNOT_APPROVE");
        assertThat(reject(transferId, MAKER)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("MAKER_CANNOT_REJECT");
        assertThat(storedStatus(transferId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void rejectionByAChecker_failsTheTransferAndGivesTheLimitBack() {
        String transferId = transferIdOf(create("60000.00"));

        MvcTestResult rejected = reject(transferId, CHECKER);

        assertThat(rejected).hasStatus(HttpStatus.OK);
        assertThat(json(rejected).get("failureCode").asString()).isEqualTo("REJECTED_BY_CHECKER");
        assertThat(json(rejected).get("checkerUserId").asString()).isEqualTo(CHECKER);
        assertThat(reservedToday()).isEqualByComparingTo("0.00");
        assertThat(outboxEventsOf(transferId, "TransferFailed")).isOne();
        assertThat(accountService.postingRequests()).isEmpty();
    }

    /** 201 is not success: the transfer was recorded and is FAILED, with the reason account-service gave. */
    @Test
    void definiteRejectionByAccountService_is201WithStatusFailedAndTheLimitBack() {
        accountService.scriptPostings(failWith(Status.Code.FAILED_PRECONDITION, "INSUFFICIENT_FUNDS"));

        MvcTestResult result = create("100.00");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(json(result).get("status").asString()).isEqualTo("FAILED");
        assertThat(json(result).get("failureCode").asString()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(reservedToday()).isEqualByComparingTo("0.00");
        assertThat(outboxEventsOf(transferIdOf(result), "TransferFailed")).isOne();
    }

    @Test
    void unknownPostingOutcome_is202ApprovedWithTheLimitStillReserved() {
        accountService.scriptPostings(failWith(Status.Code.INTERNAL, "INTERNAL_ERROR"));

        MvcTestResult result = create("100.00");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String transferId = transferIdOf(result);
        assertThat(json(result).get("status").asString()).isEqualTo("APPROVED");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(TRANSFERS + "/" + transferId);
        assertThat(storedStatus(transferId)).isEqualTo("APPROVED");
        assertThat(reservedToday()).isEqualByComparingTo("100.00");
        assertThat(outboxEventsOf(transferId, "TransferFailed")).isZero();
    }

    @Test
    void abortedPosting_isRetriedUntilItIsPosted() {
        accountService.scriptPostings(failWith(Status.Code.ABORTED, "CONCURRENT_MODIFICATION"),
                failWith(Status.Code.ABORTED, "CONCURRENT_MODIFICATION"), posted());

        MvcTestResult result = create("100.00");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(json(result).get("status").asString()).isEqualTo("POSTED");
        assertThat(accountService.postingRequests()).hasSize(3);
    }

    /** All attempts together get 800 ms here (5 s by default); a slower account-service is an unknown outcome. */
    @Test
    void postingBeyondTheTimeBudget_is202Approved() {
        for (int attempt = 0; attempt < 5; attempt++) {
            accountService.scriptPostings(slow(Duration.ofSeconds(2)));
        }

        MvcTestResult result = create("100.00");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(json(result).get("status").asString()).isEqualTo("APPROVED");
    }

    @Test
    void postingIdConflict_staysApprovedAndIsQueuedForReviewWithoutRetry() {
        accountService.scriptPostings(failWith(Status.Code.ALREADY_EXISTS, "POSTING_ID_CONFLICT"));

        MvcTestResult result = create("100.00");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String transferId = transferIdOf(result);
        assertThat(jdbcClient.sql("SELECT reason FROM transfer_review WHERE transfer_id = :id")
                .param("id", UUID.fromString(transferId)).query(String.class).single())
                .isEqualTo("POSTING_ID_CONFLICT");
        assertThat(accountService.postingRequests()).hasSize(1);
    }

    /** Requests rejected before recording leave no transfer, no event and no limit usage behind. */
    @Test
    void rejectedRequests_leaveNoTrace() {
        long outboxBefore = jdbcClient.sql("SELECT COUNT(*) FROM outbox_event").query(Long.class).single();

        assertThat(create("100000.01")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("SINGLE_TRANSACTION_LIMIT_EXCEEDED");
        targetIban = "TR269999900000000000000003";
        assertThat(create("1.00")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("ACCOUNT_NOT_FOUND");
        accountService.addAccount(UUID.randomUUID().toString(), targetIban, "TRY", false);
        assertThat(create("1.00")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("ACCOUNT_NOT_ACTIVE");
        accountService.makeLookupsUnavailable();
        assertThat(create("1.00")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("ACCOUNT_SERVICE_UNAVAILABLE");

        assertThat(transfersOfSource()).isZero();
        assertThat(reservedToday()).isEqualByComparingTo("0.00");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM outbox_event").query(Long.class).single())
                .isEqualTo(outboxBefore);
    }

    @Test
    void dailyLimit_isEnforcedAcrossRequests() {
        assertThat(create("100000.00")).hasStatus(HttpStatus.ACCEPTED);
        assertThat(create("100000.00")).hasStatus(HttpStatus.ACCEPTED);

        assertThat(create("50000.01")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("DAILY_LIMIT_EXCEEDED");
        assertThat(create("50000.00")).hasStatus(HttpStatus.CREATED);
        assertThat(reservedToday()).isEqualByComparingTo("250000.00");
    }

    @Test
    void transfer_canBeReadBack() {
        String transferId = transferIdOf(create("10.00"));

        assertThat(mvc.get().uri(TRANSFERS + "/{id}", transferId).exchange()).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("POSTED");
        assertThat(mvc.get().uri(TRANSFERS + "/{id}", UUID.randomUUID()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void openApiDocument_warnsThat201IsNotSuccess() throws Exception {
        MvcTestResult apiDocs = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(apiDocs).hasStatus(HttpStatus.OK);
        assertThat(apiDocs.getResponse().getContentAsString()).contains("does NOT mean success");
    }
}
