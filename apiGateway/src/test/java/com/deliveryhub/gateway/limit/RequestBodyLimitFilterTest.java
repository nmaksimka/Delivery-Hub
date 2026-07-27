package com.deliveryhub.gateway.limit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyLimitFilterTest {

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
            new RequestBodyProperties(DataSize.ofKilobytes(1)), JsonMapper.builder().build());
    private final AtomicBoolean forwarded = new AtomicBoolean();
    private final WebFilterChain chain = exchange -> {
        forwarded.set(true);
        return Mono.empty();
    };

    @Test
    void passesBodyWithinLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/cabinet/menu").contentLength(1024).body("x"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded).isTrue();
    }

    @Test
    void rejectsDeclaredBodyAboveLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/cabinet/menu").contentLength(1025).body("x"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    void rejectsChunkedBodyWithoutContentLength() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/restaurants").header(HttpHeaders.TRANSFER_ENCODING, "chunked"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.LENGTH_REQUIRED);
    }

    @Test
    void passesRequestWithoutBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/restaurants"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded).isTrue();
    }
}
