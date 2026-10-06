package io.github.egeozdemirr.corebank.account.infrastructure.outbox;

import io.github.egeozdemirr.corebank.account.application.port.AccountEventPublisher;
import io.github.egeozdemirr.corebank.account.domain.event.AccountOpened;
import io.github.egeozdemirr.corebank.account.infrastructure.context.RequestContext;
import io.github.egeozdemirr.corebank.contracts.EventCatalog;
import io.github.egeozdemirr.corebank.contracts.events.accounts.AccountOpenedPayloadV1;
import io.github.egeozdemirr.corebank.contracts.events.accounts.AccountOpenedV1;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Translates domain events into the published contract and stores them in the outbox. */
@Component
class OutboxAccountEventPublisher implements AccountEventPublisher {

    private static final String ACCOUNT_AGGREGATE = "Account";

    private final OutboxEventJpaRepository repository;
    private final JsonMapper jsonMapper;
    private final RequestContext requestContext;

    OutboxAccountEventPublisher(OutboxEventJpaRepository repository, JsonMapper jsonMapper,
                                RequestContext requestContext) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.requestContext = requestContext;
    }

    /** MANDATORY: an outbox row written outside the business transaction would break the outbox guarantee. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(AccountOpened event) {
        EventCatalog definition = EventCatalog.ACCOUNT_OPENED_V1;
        UUID eventId = UUID.randomUUID();
        AccountOpenedV1 message = new AccountOpenedV1()
                .withEventId(eventId)
                .withEventType(definition.eventType())
                .withSchemaVersion(definition.schemaVersion())
                .withOccurredAt(event.openedAt())
                .withCorrelationId(requestContext.correlationId())
                .withActorUserId(requestContext.actorUserId())
                .withPayload(new AccountOpenedPayloadV1()
                        .withAccountId(event.accountId().value())
                        .withIban(event.iban().value())
                        .withCustomerId(event.customerId().value())
                        .withHolderName(event.holderName().value())
                        .withCurrency(event.currency().getCurrencyCode())
                        .withOpenedAt(event.openedAt()));

        repository.save(new OutboxEventJpaEntity(eventId, definition, ACCOUNT_AGGREGATE, event.accountId().value(),
                event.accountId().toString(), jsonMapper.writeValueAsString(message), event.openedAt()));
    }
}
