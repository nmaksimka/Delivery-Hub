package com.deliveryhub.gateway.limit;

import com.deliveryhub.gateway.security.ProblemResponses;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(RequestBodyProperties.class)
public class RequestBodyLimitFilter implements WebFilter {

    private final long maxBytes;
    private final ProblemResponses problems;

    public RequestBodyLimitFilter(RequestBodyProperties properties, JsonMapper jsonMapper) {
        this.maxBytes = properties.maxSize().toBytes();
        this.problems = new ProblemResponses(jsonMapper);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        if (headers.getContentLength() > maxBytes) {
            return problems.write(exchange, HttpStatus.CONTENT_TOO_LARGE,
                    "Request body must not exceed " + maxBytes + " bytes");
        }
        if (headers.getContentLength() < 0 && headers.containsHeader(HttpHeaders.TRANSFER_ENCODING)) {
            return problems.write(exchange, HttpStatus.LENGTH_REQUIRED, "Request body must have Content-Length");
        }
        return chain.filter(exchange);
    }
}
