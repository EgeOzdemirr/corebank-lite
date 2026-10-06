package io.github.egeozdemirr.corebank.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.egeozdemirr.corebank.contracts.events.accounts.AccountOpenedPayloadV1;
import io.github.egeozdemirr.corebank.contracts.events.accounts.AccountOpenedV1;
import io.github.egeozdemirr.corebank.contracts.events.common.MonetaryAmountV1;
import io.github.egeozdemirr.corebank.contracts.events.common.TransferDetailsV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferApprovedPayloadV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferApprovedV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferPostedPayloadV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferPostedV1;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class EventSchemaConformanceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:15:30Z");
    private static final String SOURCE_IBAN = "TR809999900000000000000001";
    private static final String TARGET_IBAN = "TR539999900000000000000002";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ContractSchemaValidator validator = new ContractSchemaValidator();

    @Test
    void accountOpened_generatedDto_conformsToSchema() {
        String json = jsonMapper.writeValueAsString(accountOpened());

        assertThat(validator.validate(EventCatalog.ACCOUNT_OPENED_V1, json)).isEmpty();
    }

    @Test
    void transferApproved_belowThresholdWithoutChecker_conformsToSchema() {
        TransferApprovedV1 event = transferApproved().withPayload(approvedPayload().withCheckerUserId(null));

        String json = jsonMapper.writeValueAsString(event);

        assertThat(json).contains("\"checkerUserId\":null");
        assertThat(validator.validate(EventCatalog.TRANSFER_APPROVED_V1, json)).isEmpty();
    }

    @Test
    void transferApproved_withChecker_conformsToSchema() {
        String json = jsonMapper.writeValueAsString(transferApproved());

        assertThat(validator.validate(EventCatalog.TRANSFER_APPROVED_V1, json)).isEmpty();
    }

    @Test
    void transferPosted_generatedDto_conformsToSchema() {
        String json = jsonMapper.writeValueAsString(transferPosted());

        assertThat(validator.validate(EventCatalog.TRANSFER_POSTED_V1, json)).isEmpty();
    }

    @Test
    void event_withoutEnvelopeField_isRejected() {
        String json = jsonMapper.writeValueAsString(accountOpened().withCorrelationId(null));

        assertThat(validator.validate(EventCatalog.ACCOUNT_OPENED_V1, json)).isNotEmpty();
    }

    @Test
    void event_withAnotherEventType_isRejected() {
        String json = jsonMapper.writeValueAsString(accountOpened().withEventType("TransferPosted"));

        assertThat(validator.validate(EventCatalog.ACCOUNT_OPENED_V1, json)).isNotEmpty();
    }

    @Test
    void event_withSchemaVersionNotMatchingTopic_isRejected() {
        String json = jsonMapper.writeValueAsString(accountOpened().withSchemaVersion(2));

        assertThat(validator.validate(EventCatalog.ACCOUNT_OPENED_V1, json)).isNotEmpty();
    }

    @Test
    void amount_withMoreThanTwoFractionDigits_isRejected() {
        TransferPostedPayloadV1 payload = postedPayload();
        payload.setAmount(new MonetaryAmountV1().withAmount("10.005").withCurrency("TRY"));

        String json = jsonMapper.writeValueAsString(transferPosted().withPayload(payload));

        assertThat(validator.validate(EventCatalog.TRANSFER_POSTED_V1, json)).isNotEmpty();
    }

    @Test
    void payload_withUnknownField_isRejected() {
        ObjectNode tree = (ObjectNode) jsonMapper.valueToTree(transferPosted());
        ((ObjectNode) tree.get("payload")).put("unexpectedField", "value");

        assertThat(validator.validate(EventCatalog.TRANSFER_POSTED_V1, tree.toString())).isNotEmpty();
    }

    @Test
    void envelope_withUnknownField_isRejected() {
        ObjectNode tree = (ObjectNode) jsonMapper.valueToTree(accountOpened());
        tree.put("unexpectedField", "value");

        assertThat(validator.validate(EventCatalog.ACCOUNT_OPENED_V1, tree.toString())).isNotEmpty();
    }

    private static AccountOpenedV1 accountOpened() {
        return new AccountOpenedV1()
                .withEventId(UUID.randomUUID())
                .withEventType(EventCatalog.ACCOUNT_OPENED_V1.eventType())
                .withSchemaVersion(EventCatalog.ACCOUNT_OPENED_V1.schemaVersion())
                .withOccurredAt(NOW)
                .withCorrelationId("correlation-1")
                .withActorUserId("user-1")
                .withPayload(new AccountOpenedPayloadV1()
                        .withAccountId(UUID.randomUUID())
                        .withIban(SOURCE_IBAN)
                        .withCustomerId(UUID.randomUUID())
                        .withHolderName("Ayşe Yılmaz")
                        .withCurrency("TRY")
                        .withOpenedAt(NOW));
    }

    private static TransferApprovedV1 transferApproved() {
        return new TransferApprovedV1()
                .withEventId(UUID.randomUUID())
                .withEventType(EventCatalog.TRANSFER_APPROVED_V1.eventType())
                .withSchemaVersion(EventCatalog.TRANSFER_APPROVED_V1.schemaVersion())
                .withOccurredAt(NOW)
                .withCorrelationId("correlation-2")
                .withActorUserId("checker-1")
                .withPayload(approvedPayload());
    }

    private static TransferApprovedPayloadV1 approvedPayload() {
        return new TransferApprovedPayloadV1()
                .withApprovedAt(NOW)
                .withTransferId(UUID.randomUUID())
                .withSourceAccountId(UUID.randomUUID())
                .withSourceIban(SOURCE_IBAN)
                .withTargetAccountId(UUID.randomUUID())
                .withTargetIban(TARGET_IBAN)
                .withBeneficiaryName("Mehmet Demir")
                .withAmount(new MonetaryAmountV1().withAmount("250000.00").withCurrency("TRY"))
                .withChannel(TransferDetailsV1.Channel.INTERNET_BANKING)
                .withMakerUserId("maker-1")
                .withCheckerUserId("checker-1");
    }

    private static TransferPostedV1 transferPosted() {
        return new TransferPostedV1()
                .withEventId(UUID.randomUUID())
                .withEventType(EventCatalog.TRANSFER_POSTED_V1.eventType())
                .withSchemaVersion(EventCatalog.TRANSFER_POSTED_V1.schemaVersion())
                .withOccurredAt(NOW)
                .withCorrelationId("correlation-3")
                .withActorUserId("system")
                .withPayload(postedPayload());
    }

    private static TransferPostedPayloadV1 postedPayload() {
        return new TransferPostedPayloadV1()
                .withPostingId(UUID.randomUUID())
                .withPostedAt(NOW)
                .withTransferId(UUID.randomUUID())
                .withSourceAccountId(UUID.randomUUID())
                .withSourceIban(SOURCE_IBAN)
                .withTargetAccountId(UUID.randomUUID())
                .withTargetIban(TARGET_IBAN)
                .withBeneficiaryName("Mehmet Demir")
                .withAmount(new MonetaryAmountV1().withAmount("1500.00").withCurrency("TRY"))
                .withChannel(TransferDetailsV1.Channel.MOBILE)
                .withMakerUserId("maker-1")
                .withCheckerUserId(null);
    }
}
