package io.github.egeozdemirr.corebank.transfer;

import io.github.egeozdemirr.corebank.transfer.support.FakeAccountServiceConfiguration;
import io.github.egeozdemirr.corebank.transfer.support.FakeLedgerService;
import io.github.egeozdemirr.corebank.transfer.support.MutableClock;
import io.github.egeozdemirr.corebank.transfer.support.PostgresContainerConfiguration;
import io.github.egeozdemirr.corebank.transfer.support.TestClockConfiguration;
import io.github.egeozdemirr.corebank.transfer.support.TestTransfers;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base of the end-to-end tests: HTTP, the services, PostgreSQL and a scripted account-service on the in-process gRPC
 * transport, with a clock fixed at {@link TestTransfers#NOW}. The posting budget is shortened so that the
 * time-related cases stay fast; every test uses fresh accounts, because the context and database are shared.
 */
@SpringBootTest(properties = {
    "corebank.transfer.account-service.call-deadline=300ms",
    "corebank.transfer.account-service.posting-budget=800ms",
    "corebank.transfer.account-service.retry.initial-delay=20ms",
    "corebank.transfer.account-service.retry.max-delay=100ms"
})
@AutoConfigureMockMvc
@AutoConfigureTestGrpcTransport
@Import({PostgresContainerConfiguration.class, FakeAccountServiceConfiguration.class, TestClockConfiguration.class})
abstract class TransferApiIntegrationTest {

    static final String TRANSFERS = "/api/v1/transfers";
    static final String ACTOR_HEADER = "X-Actor-User-Id";
    static final String MAKER = "maker-1";
    static final String CHECKER = "checker-1";
    private static final String CREATE_BODY = """
            {"sourceAccountId": "%s", "targetIban": "%s", "beneficiaryName": "Mehmet Demir",
             "amount": {"amount": "%s", "currency": "TRY"}, "channel": "INTERNET_BANKING"}
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    FakeLedgerService accountService;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    JsonMapper jsonMapper;

    String sourceAccountId;
    String targetIban;

    @BeforeEach
    void freshAccountsAndClock() {
        accountService.reset();
        clock.setInstant(TestTransfers.NOW);
        sourceAccountId = UUID.randomUUID().toString();
        accountService.addAccount(sourceAccountId, TestTransfers.SOURCE_IBAN, "TRY", true);
        accountService.addAccount(UUID.randomUUID().toString(), TestTransfers.TARGET_IBAN, "TRY", true);
        targetIban = TestTransfers.TARGET_IBAN;
    }

    MvcTestResult create(String amount) {
        return create(MAKER, CREATE_BODY.formatted(sourceAccountId, targetIban, amount));
    }

    MvcTestResult create(String actor, String body) {
        return mvc.post().uri(TRANSFERS).header(ACTOR_HEADER, actor)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    MvcTestResult approve(String transferId, String actor) {
        return mvc.post().uri(TRANSFERS + "/{id}/approval", transferId).header(ACTOR_HEADER, actor).exchange();
    }

    MvcTestResult reject(String transferId, String actor) {
        return mvc.post().uri(TRANSFERS + "/{id}/rejection", transferId).header(ACTOR_HEADER, actor).exchange();
    }

    String transferIdOf(MvcTestResult result) {
        return json(result).get("transferId").asString();
    }

    JsonNode json(MvcTestResult result) {
        return jsonMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    BigDecimal reservedToday() {
        return jdbcClient.sql("SELECT COALESCE(SUM(reserved), 0) FROM daily_transfer_usage "
                        + "WHERE source_account_id = :id")
                .param("id", UUID.fromString(sourceAccountId)).query(BigDecimal.class).single();
    }

    String storedStatus(String transferId) {
        return jdbcClient.sql("SELECT status FROM transfer WHERE id = :id").param("id", UUID.fromString(transferId))
                .query(String.class).single();
    }

    long transfersOfSource() {
        return jdbcClient.sql("SELECT COUNT(*) FROM transfer WHERE source_account_id = :id")
                .param("id", UUID.fromString(sourceAccountId)).query(Long.class).single();
    }

    long outboxEventsOf(String transferId, String eventType) {
        return jdbcClient.sql("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = :id AND event_type = :type")
                .param("id", UUID.fromString(transferId)).param("type", eventType).query(Long.class).single();
    }
}
