package io.github.egeozdemirr.corebank.transfer.application.port;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferEvent;

/**
 * Publishes domain events. Implementations must record the event in the caller's transaction (transactional
 * outbox), so an event exists if and only if the business change was committed.
 */
public interface TransferEventPublisher {

    void publish(TransferEvent event);
}
