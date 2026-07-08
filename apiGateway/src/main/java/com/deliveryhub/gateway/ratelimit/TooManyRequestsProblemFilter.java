package com.deliveryhub.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;

@Component
public class TooManyRequestsProblemFilter implements GlobalFilter, Ordered {

    private static final String RETRY_AFTER_SECONDS = "1";

    private final JsonMapper jsonMapper;

    public TooManyRequestsProblemFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ProblemOnRejection response = new ProblemOnRejection(exchange.getResponse(), exchange.getRequest().getPath().value());
        return chain.filter(exchange.mutate().response(response).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private final class ProblemOnRejection extends ServerHttpResponseDecorator {

        private final String requestPath;

        private ProblemOnRejection(ServerHttpResponse delegate, String requestPath) {
            super(delegate);
            this.requestPath = requestPath;
        }

        @Override
        public Mono<Void> setComplete() {
            if (getStatusCode() != HttpStatus.TOO_MANY_REQUESTS || isCommitted()) {
                return super.setComplete();
            }
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "Request limit is exceeded, slow down and retry later");
            problem.setInstance(URI.create(requestPath));

            getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            getHeaders().set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
            DataBuffer body = bufferFactory().wrap(jsonMapper.writeValueAsBytes(problem));
            return writeWith(Mono.just(body));
        }
    }
}
