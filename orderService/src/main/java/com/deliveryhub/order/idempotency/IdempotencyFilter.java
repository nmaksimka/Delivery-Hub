package com.deliveryhub.order.idempotency;

import com.deliveryhub.order.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HEADER_REPLAYED = "Idempotent-Replayed";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E]{1,64}");
    private static final String IN_PROGRESS_RETRY_AFTER_SECONDS = "1";

    private final IdempotencyKeys keys;
    private final JsonMapper jsonMapper;
    private final List<IdempotentEndpoint> endpoints;

    public IdempotencyFilter(IdempotencyKeys keys, JsonMapper jsonMapper, IdempotencyProperties properties) {
        this.keys = keys;
        this.jsonMapper = jsonMapper;
        this.endpoints = properties.endpoints().stream().map(IdempotentEndpoint::parse).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(HEADER_IDEMPOTENCY_KEY) == null
                || userIdOf(request) == null
                || endpoints.stream().noneMatch(endpoint -> endpoint.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER_IDEMPOTENCY_KEY);
        if (!VALID_KEY.matcher(key).matches()) {
            writeProblem(request, response, HttpStatus.BAD_REQUEST, ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key must be 1 to 64 visible ASCII characters");
            return;
        }
        byte[] body = request.getInputStream().readAllBytes();
        IdempotencyScope scope = new IdempotencyScope(userIdOf(request), request.getMethod(),
                request.getRequestURI(), key);
        switch (keys.claim(scope, sha256(body))) {
            case IdempotencyClaim.Acquired _ -> proceed(new CachedBodyRequest(request, body), response, chain, scope);
            case IdempotencyClaim.Replay replay -> replay(response, replay);
            case IdempotencyClaim.InProgress _ -> {
                response.setHeader(HttpHeaders.RETRY_AFTER, IN_PROGRESS_RETRY_AFTER_SECONDS);
                writeProblem(request, response, HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "A request with this Idempotency-Key is still being processed");
            }
            case IdempotencyClaim.Mismatch _ -> writeProblem(request, response,
                    HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used with a different request body");
        }
    }

    private void proceed(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                         IdempotencyScope scope) throws ServletException, IOException {
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, captured);
        } catch (ServletException | IOException | RuntimeException ex) {
            keys.release(scope);
            throw ex;
        }
        remember(scope, captured);
        captured.copyBodyToResponse();
    }

    private void remember(IdempotencyScope scope, ContentCachingResponseWrapper captured) {
        int status = captured.getStatus();
        try {
            if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                keys.release(scope);
            } else {
                keys.complete(scope, status, jsonBodyOf(captured));
            }
        } catch (DataAccessException ex) {
            log.error("Could not store outcome of idempotent request {} {} of user {}",
                    scope.method(), scope.path(), scope.userId(), ex);
        }
    }

    private static String jsonBodyOf(ContentCachingResponseWrapper captured) {
        byte[] content = captured.getContentAsByteArray();
        String contentType = captured.getContentType();
        if (content.length == 0 || contentType == null || !contentType.contains("json")) {
            return null;
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private static void replay(HttpServletResponse response, IdempotencyClaim.Replay replay) throws IOException {
        response.setStatus(replay.status());
        response.setHeader(HEADER_REPLAYED, "true");
        if (replay.body() == null) {
            return;
        }
        response.setContentType(replay.status() >= HttpStatus.BAD_REQUEST.value()
                ? MediaType.APPLICATION_PROBLEM_JSON_VALUE
                : MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(replay.body());
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                              ErrorCode code, String detail) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("code", code.name());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }

    private static Long userIdOf(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
