package io.github.egeozdemirr.corebank.transfer.infrastructure.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the correlation id and the acting user into the logging context for the duration of a request and echoes
 * the correlation id back to the caller.
 *
 * <p>Until authentication arrives (roadmap week 4) the actor is taken from a plain header; header values are
 * validated so that callers cannot inject arbitrary text into logs and events.
 */
@Component
class RequestContextHeaderFilter extends OncePerRequestFilter {

    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    static final String ACTOR_USER_ID_HEADER = "X-Actor-User-Id";
    static final String ANONYMOUS_ACTOR = "anonymous";

    private static final Pattern SAFE_HEADER_VALUE = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = safeValue(request.getHeader(CORRELATION_ID_HEADER), UUID.randomUUID().toString());
        String actorUserId = safeValue(request.getHeader(ACTOR_USER_ID_HEADER), ANONYMOUS_ACTOR);
        MDC.put(MdcRequestContext.CORRELATION_ID_KEY, correlationId);
        MDC.put(MdcRequestContext.ACTOR_USER_ID_KEY, actorUserId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcRequestContext.CORRELATION_ID_KEY);
            MDC.remove(MdcRequestContext.ACTOR_USER_ID_KEY);
        }
    }

    private static String safeValue(String headerValue, String fallback) {
        if (headerValue == null || !SAFE_HEADER_VALUE.matcher(headerValue).matches()) {
            return fallback;
        }
        return headerValue;
    }
}
