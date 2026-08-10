package com.deliveryhub.order.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    private static final String BODY = "{\"restaurantId\":1}";
    private static final String BODY_SHA256 = "2e9f09fca7e6bfbfb3cc852ab5004aac4650382d727c06a90eeef9d987612dea";
    private static final IdempotencyScope SCOPE = new IdempotencyScope(42L, "POST", "/api/orders", "attempt-1");

    private final IdempotencyKeys keys = mock(IdempotencyKeys.class);
    private IdempotencyFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter(keys, JsonMapper.builder().build(),
                new IdempotencyProperties(Duration.ofHours(24), Duration.ofSeconds(60), DataSize.ofKilobytes(64), List.of("POST /api/orders", "POST /api/orders/{id}/pay")));
        response = new MockHttpServletResponse();
    }

    @Test
    void passesRequestWithoutKeyStraightThrough() throws Exception {
        MockHttpServletRequest request = orderRequest();
        request.removeHeader(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(keys);
    }

    @Test
    void ignoresEndpointsThatAreNotConfigured() throws Exception {
        MockHttpServletRequest request = orderRequest();
        request.setRequestURI("/api/orders/5/cancel");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(keys);
    }

    @Test
    void matchesEndpointByPathPattern() throws Exception {
        MockHttpServletRequest request = orderRequest();
        request.setRequestURI("/api/orders/5/pay");
        when(keys.claim(any(), anyString())).thenReturn(new IdempotencyClaim.Acquired());

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));

        verify(keys).complete(new IdempotencyScope(42L, "POST", "/api/orders/5/pay", "attempt-1"), 200, null);
    }

    @Test
    void rejectsKeyLongerThanSixtyFourCharacters() throws Exception {
        MockHttpServletRequest request = orderRequest();
        request.removeHeader(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY);
        request.addHeader(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, "k".repeat(65));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":\"IDEMPOTENCY_KEY_INVALID\"");
        verifyNoInteractions(keys, chain);
    }

    @Test
    void rejectsDeclaredBodyLargerThanLimitWithoutReadingIt() throws Exception {
        MockHttpServletRequest request = orderRequest();
        request.setContent(new byte[64 * 1024 + 1]);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
        verifyNoInteractions(keys, chain);
    }

    @Test
    void rejectsChunkedBodyThatGrowsPastLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.addHeader(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, "attempt-1");
        request.addHeader("X-User-Id", "42");
        request.setContent(new byte[64 * 1024 + 1]);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(keys, chain);
    }

    @Test
    void storesSuccessfulResponseAndLetsControllerReadTheBody() throws Exception {
        when(keys.claim(SCOPE, BODY_SHA256)).thenReturn(new IdempotencyClaim.Acquired());
        AtomicReference<String> bodySeenByController = new AtomicReference<>();

        filter.doFilter(orderRequest(), response, (req, res) -> {
            bodySeenByController.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(201);
            http.setContentType(MediaType.APPLICATION_JSON_VALUE);
            http.getWriter().write("{\"id\":7}");
        });

        assertThat(bodySeenByController).hasValue(BODY);
        verify(keys).complete(SCOPE, 201, "{\"id\":7}");
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":7}");
    }

    @Test
    void remembersClientErrors() throws Exception {
        when(keys.claim(SCOPE, BODY_SHA256)).thenReturn(new IdempotencyClaim.Acquired());

        filter.doFilter(orderRequest(), response, (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(409);
            http.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            http.getWriter().write("{\"code\":\"CART_EMPTY\"}");
        });

        verify(keys).complete(SCOPE, 409, "{\"code\":\"CART_EMPTY\"}");
        verify(keys, never()).release(any());
    }

    @Test
    void releasesKeyAfterServerError() throws Exception {
        when(keys.claim(SCOPE, BODY_SHA256)).thenReturn(new IdempotencyClaim.Acquired());

        filter.doFilter(orderRequest(), response, (req, res) -> ((HttpServletResponse) res).setStatus(503));

        verify(keys).release(SCOPE);
        verify(keys, never()).complete(any(), anyInt(), any());
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void releasesKeyWhenRequestHandlingThrows() {
        when(keys.claim(SCOPE, BODY_SHA256)).thenReturn(new IdempotencyClaim.Acquired());

        assertThatThrownBy(() -> filter.doFilter(orderRequest(), response, (req, res) -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        verify(keys).release(SCOPE);
    }

    @Test
    void replaysStoredResponseWithoutCallingController() throws Exception {
        when(keys.claim(SCOPE, BODY_SHA256)).thenReturn(new IdempotencyClaim.Replay(201, "{\"id\":7}"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(orderRequest(), response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader(IdempotencyFilter.HEADER_REPLAYED)).isEqualTo("true");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":7}");
    }

    @Test
    void rejectsSameKeyWithDifferentBody() throws Exception {
        when(keys.claim(eq(SCOPE), anyString())).thenReturn(new IdempotencyClaim.Mismatch());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(orderRequest(), response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"code\":\"IDEMPOTENCY_KEY_REUSED\"");
    }

    @Test
    void asksToRetryWhileFirstRequestIsInProgress() throws Exception {
        when(keys.claim(eq(SCOPE), anyString())).thenReturn(new IdempotencyClaim.InProgress());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(orderRequest(), response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentAsString()).contains("\"code\":\"IDEMPOTENCY_IN_PROGRESS\"");
    }

    private static MockHttpServletRequest orderRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, "attempt-1");
        request.addHeader("X-User-Id", "42");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
