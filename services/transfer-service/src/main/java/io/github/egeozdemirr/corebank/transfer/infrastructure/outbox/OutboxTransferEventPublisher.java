package io.github.egeozdemirr.corebank.transfer.infrastructure.outbox;

import io.github.egeozdemirr.corebank.transfer.application.port.TransferEventPublisher;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferEvent;
import io.github.egeozdemirr.corebank.transfer.infrastructure.context.RequestContext;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Stores transfer events in the outbox, partitioned by the source account so its events stay in order. */
@Component
class OutboxTransferEventPublisher implements TransferEventPublisher {

    private static final String TRANSFER_AGGREGATE = "Transfer";

    private final OutboxEventJpaRepository repository;
    private final JsonMapper jsonMapper;
    private final RequestContext requestContext;

    OutboxTransferEventPublisher(OutboxEventJpaRepository repository, JsonMapper jsonMapper,
                                 RequestContext requestContext) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.requestContext = requestContext;
    }

    /** MANDATORY: an outbox row written outside the business transaction would break the outbox guarantee. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(TransferEvent event) {
        UUID eventId = UUID.randomUUID();
        Object message = TransferEventMapper.toMessage(event, eventId, requestContext.correlationId(),
                requestContext.actorUserId());
        repository.save(new OutboxEventJpaEntity(eventId, TransferEventMapper.definitionOf(event), TRANSFER_AGGREGATE,
                event.transferId().value(), event.order().source().accountId().toString(),
                jsonMapper.writeValueAsString(message), event.occurredAt()));
    }
}
