package com.deliveryhub.payment.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenFilterTest {

    private static final String TOKEN = "secret-token";

    private final InternalTokenFilter filter = new InternalTokenFilter(TOKEN);

    @Test
    void internalPathWithoutTokenIsUnauthorized() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest("POST", "/internal/payments/5/hold"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void internalPathWithWrongTokenIsUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/payments/5/capture");
        request.addHeader(InternalTokenFilter.HEADER_INTERNAL_TOKEN, "guess");

        assertThat(run(request).getStatus()).isEqualTo(401);
    }

    @Test
    void internalPathWithTokenPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/payments/5/capture");
        request.addHeader(InternalTokenFilter.HEADER_INTERNAL_TOKEN, TOKEN);

        assertThat(run(request).getStatus()).isEqualTo(200);
    }

    @Test
    void identityHeadersWithoutTokenAreUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/wallet");
        request.addHeader("X-User-Id", "1");

        assertThat(run(request).getStatus()).isEqualTo(401);
    }

    @Test
    void publicRequestWithoutIdentityPasses() throws Exception {
        assertThat(run(new MockHttpServletRequest("GET", "/actuator/health")).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/%69nternal/x/1", "//internal/x/1", "/internal;a=b/x/1", "/internal"})
    void rejectsEncodedInternalPathWithoutToken(String uri) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", uri), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
