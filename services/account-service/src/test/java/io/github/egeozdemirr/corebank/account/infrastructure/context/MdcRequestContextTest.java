package io.github.egeozdemirr.corebank.account.infrastructure.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcRequestContextTest {

    private final MdcRequestContext context = new MdcRequestContext();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void readsValuesPlacedByTheFilter() {
        MDC.put(MdcRequestContext.CORRELATION_ID_KEY, "trace-1");
        MDC.put(MdcRequestContext.ACTOR_USER_ID_KEY, "maker-7");

        assertThat(context.correlationId()).isEqualTo("trace-1");
        assertThat(context.actorUserId()).isEqualTo("maker-7");
    }

    @Test
    void outsideARequest_attributesWorkToTheSystem() {
        assertThat(context.actorUserId()).isEqualTo(MdcRequestContext.SYSTEM_ACTOR);
        assertThat(context.correlationId()).isNotBlank();
    }
}
