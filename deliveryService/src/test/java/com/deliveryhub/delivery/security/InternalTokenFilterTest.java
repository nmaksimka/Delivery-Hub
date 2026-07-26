package com.deliveryhub.delivery.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenFilterTest {

    private final InternalTokenFilter filter = new InternalTokenFilter("gateway-secret");

    @Test
    void passesIdentityHeadersSignedByGatewayToken() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Role", "ADMIN");
        request.addHeader(InternalTokenFilter.HEADER_INTERNAL_TOKEN, "gateway-secret");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsIdentityHeadersWithoutToken() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsIdentityHeadersWithWrongToken() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("x-user-id", "1");
        request.addHeader(InternalTokenFilter.HEADER_INTERNAL_TOKEN, "gateway-secreT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void passesAnonymousRequestWithoutToken() throws Exception {
        MockHttpServletRequest request = request();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/restaurants");
    }
}
