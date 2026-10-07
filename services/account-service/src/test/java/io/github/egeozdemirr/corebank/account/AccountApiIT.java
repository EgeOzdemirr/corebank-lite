package io.github.egeozdemirr.corebank.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import io.github.egeozdemirr.corebank.account.support.PostgresContainerConfiguration;
import io.github.egeozdemirr.corebank.account.support.SyntheticData;
import io.github.egeozdemirr.corebank.contracts.ContractSchemaValidator;
import io.github.egeozdemirr.corebank.contracts.EventCatalog;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End to end through HTTP, the service layer, Flyway-managed PostgreSQL and the outbox. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class AccountApiIT {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final UUID TRY_FUNDING_ACCOUNT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper jsonMapper;

    private final ContractSchemaValidator contractValidator = new ContractSchemaValidator();

    @Test
    void openAccountWithDeposit_updatesBalancesLedgerAndOutboxTogether() {
        BigDecimal fundingBefore = balanceOf(TRY_FUNDING_ACCOUNT);

        MvcTestResult created = openAccount("TRY", "1500.00", "trace-it-1", "maker-1");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        UUID accountId = UUID.fromString(read(created).get("accountId").asString());
        assertThat(balanceOf(accountId)).isEqualByComparingTo("1500.00");
        assertThat(balanceOf(TRY_FUNDING_ACCOUNT)).isEqualByComparingTo(fundingBefore.subtract(new BigDecimal("1500")));

        assertThat(mvc.get().uri(ACCOUNTS + "/{id}/balance", accountId).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.balance.amount").isEqualTo("1500.00");
        assertThat(mvc.get().uri(ACCOUNTS + "/{id}/ledger-entries", accountId).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.entries[0].direction").isEqualTo("CREDIT");
        assertThat(signedTotalOfPostingsTouching(accountId)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fundingAccount_isNeitherWrittenNorStoresABalance() {
        long versionBefore = fundingVersion();

        openAccount("TRY", "20.00", "trace-it-5", "maker-5");

        assertThat(fundingVersion()).isEqualTo(versionBefore);
        assertThat(jdbcClient.sql("SELECT balance IS NULL FROM account WHERE id = :id")
                .param("id", TRY_FUNDING_ACCOUNT).query(Boolean.class).single()).isTrue();
        String derivedBalance = balanceOf(TRY_FUNDING_ACCOUNT).toPlainString();
        assertThat(mvc.get().uri(ACCOUNTS + "/{id}", TRY_FUNDING_ACCOUNT).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.balance.amount").isEqualTo(derivedBalance);
    }

    @Test
    void openAccount_writesContractConformingEventToOutbox() {
        MvcTestResult created = openAccount("TRY", null, "trace-it-2", "maker-2");
        String accountId = read(created).get("accountId").asString();

        Map<String, Object> row = jdbcClient.sql("""
                        SELECT topic, partition_key, event_type, schema_version, payload::text AS payload
                          FROM outbox_event WHERE aggregate_id = :id
                        """)
                .param("id", UUID.fromString(accountId))
                .query().singleRow();

        assertThat(row).containsEntry("topic", EventCatalog.ACCOUNT_OPENED_V1.topic())
                .containsEntry("partition_key", accountId)
                .containsEntry("event_type", "AccountOpened")
                .containsEntry("schema_version", 1);
        String payload = (String) row.get("payload");
        assertThat(contractValidator.validate(EventCatalog.ACCOUNT_OPENED_V1, payload)).isEmpty();
        JsonNode event = jsonMapper.readTree(payload);
        assertThat(event.get("correlationId").asString()).isEqualTo("trace-it-2");
        assertThat(event.get("actorUserId").asString()).isEqualTo("maker-2");
        assertThat(event.get("payload").get("accountId").asString()).isEqualTo(accountId);
    }

    @Test
    void generatedIbans_areValidAndUnique() {
        String first = read(openAccount("TRY", null, "trace-it-3", "maker-3")).get("iban").asString();
        String second = read(openAccount("TRY", null, "trace-it-3", "maker-3")).get("iban").asString();

        assertThat(new Iban(first).value()).startsWith("TR").contains("99999");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectedOpening_leavesNoTrace() {
        long accountsBefore = count("account");
        long outboxBefore = count("outbox_event");

        assertThat(openAccount("GBP", "10.00", "trace-it-4", "maker-4"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("UNSUPPORTED_CURRENCY");
        assertThat(count("account")).isEqualTo(accountsBefore);
        assertThat(count("outbox_event")).isEqualTo(outboxBefore);
    }

    @Test
    void unknownAccount_isNotFound() {
        assertThat(mvc.get().uri(ACCOUNTS + "/{id}/ledger-entries", UUID.randomUUID()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void openApiDocument_isPublished() {
        assertThat(mvc.get().uri("/v3/api-docs").exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.info.title").isEqualTo("account-service");
    }

    private MvcTestResult openAccount(String currency, String deposit, String correlationId, String actor) {
        String depositField = deposit == null ? "" : ", \"openingDeposit\": \"" + deposit + "\"";
        String body = """
                {"customerId": "%s", "holderName": "%s", "holderTckn": "%s", "currency": "%s"%s}
                """.formatted(UUID.randomUUID(), SyntheticData.fullName(), SyntheticData.tckn(), currency,
                depositField);
        return mvc.post().uri(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", correlationId)
                .header("X-Actor-User-Id", actor)
                .content(body)
                .exchange();
    }

    private JsonNode read(MvcTestResult result) {
        return jsonMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    /** Through the API, so funding accounts report their ledger-derived balance (ADR-0003). */
    private BigDecimal balanceOf(UUID accountId) {
        MvcTestResult balance = mvc.get().uri(ACCOUNTS + "/{id}/balance", accountId).exchange();
        return new BigDecimal(read(balance).get("balance").get("amount").asString());
    }

    private BigDecimal signedTotalOfPostingsTouching(UUID accountId) {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
                          FROM ledger_entry
                         WHERE posting_id IN (SELECT posting_id FROM ledger_entry WHERE account_id = :id)
                        """)
                .param("id", accountId).query(BigDecimal.class).single();
    }

    private long fundingVersion() {
        return jdbcClient.sql("SELECT version FROM account WHERE id = :id")
                .param("id", TRY_FUNDING_ACCOUNT).query(Long.class).single();
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }
}
