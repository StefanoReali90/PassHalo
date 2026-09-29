package org.spring.passhalo.user.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestCorrelationFilterTest {
    @Test
    void exposesRequestIdAndClearsItAfterFailure() {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            String requestId = MDC.get("requestId");
            assertNotNull(requestId);
            assertEquals(requestId, response.getHeader("X-Request-ID"));
            throw new ServletException("Test failure");
        }));

        assertNull(MDC.get("requestId"));
    }
}
