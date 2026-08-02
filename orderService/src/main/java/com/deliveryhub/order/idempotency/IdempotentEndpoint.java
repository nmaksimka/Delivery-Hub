package com.deliveryhub.order.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Locale;

record IdempotentEndpoint(String method, PathPattern pattern) {

    static IdempotentEndpoint parse(String definition) {
        String[] parts = definition.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Idempotent endpoint must look like 'POST /path': " + definition);
        }
        return new IdempotentEndpoint(parts[0].toUpperCase(Locale.ROOT), PathPatternParser.defaultInstance.parse(parts[1]));
    }

    boolean matches(HttpServletRequest request) {
        return method.equals(request.getMethod())
                && pattern.matches(PathContainer.parsePath(request.getRequestURI()));
    }
}
