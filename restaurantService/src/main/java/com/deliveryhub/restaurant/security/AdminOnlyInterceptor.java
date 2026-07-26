package com.deliveryhub.restaurant.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

public class AdminOnlyInterceptor implements HandlerInterceptor {

    public static final String ADMIN_ROLE = "ADMIN";
    public static final String ADMIN_PATHS = "/api/admin/**";
    public static final String CATALOG_PATHS = "/api/restaurants/**";

    private static final Set<String> READ_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isCatalogRead(request) || ADMIN_ROLE.equals(request.getHeader(OwnerIdentityResolver.HEADER_USER_ROLE))) {
            return true;
        }
        throw new AdminOnlyException();
    }

    private static boolean isCatalogRead(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/restaurants") && READ_METHODS.contains(request.getMethod());
    }
}
