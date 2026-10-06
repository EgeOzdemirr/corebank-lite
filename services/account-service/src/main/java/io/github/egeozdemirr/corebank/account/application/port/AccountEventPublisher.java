package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.domain.event.AccountOpened;

/**
 * Publishes domain events. Implementations must record the event in the caller's transaction (transactional
 * outbox), so an event exists if and only if the business change was committed.
 */
public interface AccountEventPublisher {

    void publish(AccountOpened event);
}
