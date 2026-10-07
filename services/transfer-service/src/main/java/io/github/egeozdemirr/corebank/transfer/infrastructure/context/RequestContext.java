package io.github.egeozdemirr.corebank.transfer.infrastructure.context;

/**
 * Who is acting and which request chain we are in. Kept behind an interface so that roadmap week 4 can take the
 * actor from the validated JWT instead of a header without touching any caller.
 */
public interface RequestContext {

    String correlationId();

    String actorUserId();
}
