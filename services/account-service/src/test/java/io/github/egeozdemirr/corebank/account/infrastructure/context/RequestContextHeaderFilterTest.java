package io.github.egeozdemirr.corebank.account.infrastructure.context;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextHeaderFilterTest {

    private final RequestContextHeaderFilter filter = new RequestContextHeaderFilter();

    @Test
    void exposesHeadersDuringTheRequestAndClearsThemAfterwards() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaderFilter.CORRELATION_ID_HEADER, "trace-9");
        request.addHeader(RequestContextHeaderFilter.ACTOR_USER_ID_HEADER, "checker-2");

        Map<String, String> seenDuringRequest = mdcSeenDuring(request);

        assertThat(seenDuringRequest)
                .containsEntry(MdcRequestContext.CORRELATION_ID_KEY, "trace-9")
                .containsEntry(MdcRequestContext.ACTOR_USER_ID_KEY, "checker-2");
        assertThat(MDC.get(MdcRequestContext.CORRELATION_ID_KEY)).isNull();
        assertThat(MDC.get(MdcRequestContext.ACTOR_USER_ID_KEY)).isNull();
    }

    @Test
    void unsafeActor_becomesAnonymous() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaderFilter.ACTOR_USER_ID_HEADER, "<script>");

        assertThat(mdcSeenDuring(request))
                .containsEntry(MdcRequestContext.ACTOR_USER_ID_KEY, RequestContextHeaderFilter.ANONYMOUS_ACTOR);
    }

    private Map<String, String> mdcSeenDuring(MockHttpServletRequest request) throws ServletException, IOException {
        Map<String, String> seen = new HashMap<>();
        GenericServlet recorder = new GenericServlet() {
            @Override
            public void service(ServletRequest servletRequest, ServletResponse servletResponse) {
                seen.putAll(MDC.getCopyOfContextMap());
            }
        };
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain(recorder));
        return seen;
    }
}
