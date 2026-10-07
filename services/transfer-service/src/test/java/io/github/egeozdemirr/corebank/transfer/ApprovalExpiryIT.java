package io.github.egeozdemirr.corebank.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.egeozdemirr.corebank.transfer.support.TestTransfers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/**
 * An approval that comes after the business day of the request expires the transfer instead. The expiry is committed
 * in its own transaction before the 409 is returned, so the 409 does not roll it back.
 */
class ApprovalExpiryIT extends TransferApiIntegrationTest {

    private static final LocalDate REQUEST_DAY = TestTransfers.ISTANBUL.businessDayOf(TestTransfers.NOW);

    @Test
    void approvalAfterTheBusinessDay_is409AndTheExpiryStays() {
        String transferId = transferIdOf(create("60000.00"));
        clock.setInstant(TestTransfers.NEXT_BUSINESS_DAY);

        assertThat(approve(transferId, CHECKER)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("APPROVAL_EXPIRED");

        Map<String, Object> stored = jdbcClient.sql(
                        "SELECT status, failure_code, checker_user_id, failed_at FROM transfer WHERE id = :id")
                .param("id", UUID.fromString(transferId)).query().singleRow();
        assertThat(stored).containsEntry("status", "FAILED").containsEntry("failure_code", "APPROVAL_EXPIRED");
        assertThat(stored.get("checker_user_id")).isNull();
        assertThat(stored.get("failed_at")).isNotNull();
        assertThat(accountService.postingRequests()).isEmpty();
    }

    @Test
    void expiry_givesTheLimitBackToTheDayOfTheRequest() {
        String transferId = transferIdOf(create("60000.00"));
        clock.setInstant(TestTransfers.NEXT_BUSINESS_DAY);

        reject(transferId, CHECKER);

        assertThat(jdbcClient.sql("SELECT reserved FROM daily_transfer_usage WHERE source_account_id = :id "
                        + "AND business_day = :day")
                .param("id", UUID.fromString(sourceAccountId)).param("day", REQUEST_DAY)
                .query(BigDecimal.class).single()).isEqualByComparingTo("0.00");
        assertThat(storedStatus(transferId)).isEqualTo("FAILED");
    }

    /** The expiry was triggered by the checker's request, so the checker is the event's actor, not its checker. */
    @Test
    void expiryEvent_namesTheRequestingUserAsActorAndHasNoChecker() {
        String transferId = transferIdOf(create("60000.00"));
        clock.setInstant(TestTransfers.NEXT_BUSINESS_DAY);

        approve(transferId, CHECKER);

        String payload = jdbcClient.sql("SELECT payload::text FROM outbox_event WHERE aggregate_id = :id "
                        + "AND event_type = 'TransferFailed'")
                .param("id", UUID.fromString(transferId)).query(String.class).single();
        JsonNode event = jsonMapper.readTree(payload);
        assertThat(event.get("actorUserId").asString()).isEqualTo(CHECKER);
        assertThat(event.get("payload").get("checkerUserId").isNull()).isTrue();
        assertThat(event.get("payload").get("failureCode").asString()).isEqualTo("APPROVAL_EXPIRED");
    }

    @Test
    void approvalOnTheLastInstantOfTheDay_isStillOnTime() {
        String transferId = transferIdOf(create("60000.00"));
        clock.setInstant(TestTransfers.NEXT_BUSINESS_DAY.minusNanos(1_000));

        assertThat(approve(transferId, CHECKER)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("POSTED");
    }
}
