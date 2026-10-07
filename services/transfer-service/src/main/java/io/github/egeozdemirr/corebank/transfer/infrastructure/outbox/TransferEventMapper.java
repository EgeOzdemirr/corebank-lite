package io.github.egeozdemirr.corebank.transfer.infrastructure.outbox;

import io.github.egeozdemirr.corebank.contracts.EventCatalog;
import io.github.egeozdemirr.corebank.contracts.events.common.MonetaryAmountV1;
import io.github.egeozdemirr.corebank.contracts.events.common.TransferDetailsV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferApprovedPayloadV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferApprovedV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferFailedPayloadV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferFailedV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferPostedPayloadV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferPostedV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferRequestedPayloadV1;
import io.github.egeozdemirr.corebank.contracts.events.transfers.TransferRequestedV1;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferApproved;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferEvent;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferFailed;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferPosted;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferRequested;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import java.util.UUID;

/** Translates domain events into the published contracts; the only place that knows both. */
final class TransferEventMapper {

    private TransferEventMapper() {
    }

    static EventCatalog definitionOf(TransferEvent event) {
        return switch (event) {
            case TransferRequested requested -> EventCatalog.TRANSFER_REQUESTED_V1;
            case TransferApproved approved -> EventCatalog.TRANSFER_APPROVED_V1;
            case TransferPosted posted -> EventCatalog.TRANSFER_POSTED_V1;
            case TransferFailed failed -> EventCatalog.TRANSFER_FAILED_V1;
        };
    }

    /** The envelope's eventId, correlationId and actorUserId come from the caller. */
    static Object toMessage(TransferEvent event, UUID eventId, String correlationId, String actorUserId) {
        EventCatalog definition = definitionOf(event);
        return switch (event) {
            case TransferRequested requested -> new TransferRequestedV1()
                    .withEventId(eventId).withEventType(definition.eventType())
                    .withSchemaVersion(definition.schemaVersion()).withOccurredAt(event.occurredAt())
                    .withCorrelationId(correlationId).withActorUserId(actorUserId)
                    .withPayload(details(new TransferRequestedPayloadV1()
                            .withRequestedAt(requested.requestedAt())
                            .withApprovalRequired(requested.approvalRequired()), event));
            case TransferApproved approved -> new TransferApprovedV1()
                    .withEventId(eventId).withEventType(definition.eventType())
                    .withSchemaVersion(definition.schemaVersion()).withOccurredAt(event.occurredAt())
                    .withCorrelationId(correlationId).withActorUserId(actorUserId)
                    .withPayload(details(new TransferApprovedPayloadV1().withApprovedAt(approved.approvedAt()),
                            event));
            case TransferPosted posted -> new TransferPostedV1()
                    .withEventId(eventId).withEventType(definition.eventType())
                    .withSchemaVersion(definition.schemaVersion()).withOccurredAt(event.occurredAt())
                    .withCorrelationId(correlationId).withActorUserId(actorUserId)
                    .withPayload(details(new TransferPostedPayloadV1()
                            // The ledger posting of a transfer uses the transfer id as its posting id (ADR-0004).
                            .withPostingId(posted.transferId().value())
                            .withPostedAt(posted.postedAt()), event));
            case TransferFailed failed -> new TransferFailedV1()
                    .withEventId(eventId).withEventType(definition.eventType())
                    .withSchemaVersion(definition.schemaVersion()).withOccurredAt(event.occurredAt())
                    .withCorrelationId(correlationId).withActorUserId(actorUserId)
                    .withPayload(details(new TransferFailedPayloadV1()
                            .withFailureCode(failed.reason().code())
                            .withFailedAt(failed.failedAt()), event));
        };
    }

    /** The fields every transfer event shares, as the monitoring rules need them (ADR-0001). */
    private static <T extends TransferDetailsV1> T details(T payload, TransferEvent event) {
        TransferOrder order = event.order();
        payload.setTransferId(event.transferId().value());
        payload.setSourceAccountId(order.source().accountId().value());
        payload.setSourceIban(order.source().iban().value());
        payload.setTargetAccountId(order.target().accountId().value());
        payload.setTargetIban(order.target().iban().value());
        payload.setBeneficiaryName(order.beneficiaryName().value());
        payload.setAmount(new MonetaryAmountV1()
                .withAmount(order.amount().amount().toPlainString())
                .withCurrency(order.amount().currency().getCurrencyCode()));
        payload.setChannel(TransferDetailsV1.Channel.valueOf(order.channel().name()));
        payload.setMakerUserId(order.maker().value());
        payload.setCheckerUserId(event.checker().map(UserId::value).orElse(null));
        return payload;
    }
}
