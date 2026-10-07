package io.github.egeozdemirr.corebank.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.CHECKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.ISTANBUL;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NOW;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.POLICY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.contracts.ContractSchemaValidator;
import io.github.egeozdemirr.corebank.contracts.EventCatalog;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferEventPublisher;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferEvent;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.support.PostgresContainerConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Every transfer event lands in the outbox exactly as the published contract describes it. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class TransferOutboxIT {

    @Autowired
    private TransferEventPublisher publisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper jsonMapper;

    private final ContractSchemaValidator validator = new ContractSchemaValidator();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void autoApprovedTransfer_publishesRequestedApprovedAndPostedWithoutChecker() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("100.00"), POLICY, NOW);
        transfer.markPosted(NOW.plusSeconds(1));

        publishAll(transfer.pullEvents(), "maker-1");

        List<Map<String, Object>> rows = outboxRowsOf(transfer);
        assertThat(rows).extracting(row -> row.get("event_type"))
                .containsExactly("TransferRequested", "TransferApproved", "TransferPosted");
        for (Map<String, Object> row : rows) {
            assertConforms(row);
            JsonNode event = jsonMapper.readTree((String) row.get("payload"));
            assertThat(event.get("payload").get("checkerUserId").isNull()).isTrue();
            assertThat(event.get("actorUserId").asString()).isEqualTo("maker-1");
            assertThat(event.get("correlationId").asString()).isEqualTo("trace-outbox");
            assertThat(row.get("partition_key")).isEqualTo(transfer.order().source().accountId().toString());
        }
        JsonNode posted = jsonMapper.readTree((String) rows.get(2).get("payload"));
        assertThat(posted.get("payload").get("postingId").asString()).isEqualTo(transfer.id().toString());
    }

    @Test
    void rejectedTransfer_publishesTheCheckerAndTheCode() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("75000.00"), POLICY, NOW);
        transfer.reject(CHECKER, ISTANBUL, NOW.plusSeconds(30));

        publishAll(transfer.pullEvents(), CHECKER.value());

        Map<String, Object> failed = outboxRowsOf(transfer).getLast();
        assertConforms(failed);
        JsonNode event = jsonMapper.readTree((String) failed.get("payload"));
        assertThat(event.get("payload").get("failureCode").asString()).isEqualTo("REJECTED_BY_CHECKER");
        assertThat(event.get("payload").get("checkerUserId").asString()).isEqualTo(CHECKER.value());
        assertThat(event.get("payload").get("amount").get("amount").asString()).isEqualTo("75000.00");
    }

    @Test
    void approvedByChecker_publishesTheChecker() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("75000.00"), POLICY, NOW);
        transfer.approve(CHECKER, ISTANBUL, NOW.plusSeconds(30));
        transfer.markFailed(new FailureReason("INSUFFICIENT_FUNDS"), NOW.plusSeconds(31));

        publishAll(transfer.pullEvents(), CHECKER.value());

        List<Map<String, Object>> rows = outboxRowsOf(transfer);
        rows.forEach(this::assertConforms);
        assertThat(jsonMapper.readTree((String) rows.get(1).get("payload")).get("payload").get("checkerUserId")
                .asString()).isEqualTo(CHECKER.value());
    }

    @Test
    void publishing_outsideATransaction_isRefused() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("1.00"), POLICY, NOW);
        TransferEvent requested = transfer.pullEvents().getFirst();

        assertThatThrownBy(() -> publisher.publish(requested))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(outboxRowsOf(transfer)).isEmpty();
    }

    private void publishAll(List<TransferEvent> events, String actor) {
        MDC.put("correlationId", "trace-outbox");
        MDC.put("actorUserId", actor);
        transactionTemplate.executeWithoutResult(status -> events.forEach(publisher::publish));
    }

    private List<Map<String, Object>> outboxRowsOf(Transfer transfer) {
        return new ArrayList<>(jdbcClient.sql("""
                        SELECT event_type, topic, partition_key, payload::text AS payload
                          FROM outbox_event WHERE aggregate_id = :id ORDER BY occurred_at, event_type DESC
                        """)
                .param("id", transfer.id().value()).query().listOfRows());
    }

    private void assertConforms(Map<String, Object> row) {
        EventCatalog definition = Arrays.stream(EventCatalog.values())
                .filter(candidate -> candidate.eventType().equals(row.get("event_type")))
                .findFirst().orElseThrow();
        assertThat(row.get("topic")).isEqualTo(definition.topic());
        assertThat(validator.validate(definition, (String) row.get("payload"))).isEmpty();
    }
}
