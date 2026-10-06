package io.github.egeozdemirr.corebank.account.infrastructure.context;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Reads the values {@link RequestContextHeaderFilter} put into the logging context. */
@Component
class MdcRequestContext implements RequestContext {

    static final String CORRELATION_ID_KEY = "correlationId";
    static final String ACTOR_USER_ID_KEY = "actorUserId";

    /** Work that does not start from an HTTP request (batch jobs, tests) is attributed to the system itself. */
    static final String SYSTEM_ACTOR = "system";

    @Override
    public String correlationId() {
        return Optional.ofNullable(MDC.get(CORRELATION_ID_KEY)).orElseGet(() -> UUID.randomUUID().toString());
    }

    @Override
    public String actorUserId() {
        return Optional.ofNullable(MDC.get(ACTOR_USER_ID_KEY)).orElse(SYSTEM_ACTOR);
    }
}
