package com.deliveryhub.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Locale;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";
    private static final String IDENTITY_HEADER_PREFIX = "x-user-";
    private static final String PROBLEM = """
            {"type":"about:blank","title":"Unauthorized","status":401,\
            "detail":"Identity headers are accepted only from the gateway"}""";

    private final byte[] expectedToken;

    public InternalTokenFilter(@Value("${app.internal-token}") String internalToken) {
        Assert.hasText(internalToken, "Internal token is required");
        this.expectedToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (carriesIdentity(request) && !hasValidToken(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(PROBLEM);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean carriesIdentity(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
                .anyMatch(name -> name.toLowerCase(Locale.ROOT).startsWith(IDENTITY_HEADER_PREFIX));
    }

    private boolean hasValidToken(HttpServletRequest request) {
        String token = request.getHeader(HEADER_INTERNAL_TOKEN);
        return token != null && MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
    }
}
