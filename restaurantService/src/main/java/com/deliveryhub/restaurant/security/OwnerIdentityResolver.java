package com.deliveryhub.restaurant.security;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class OwnerIdentityResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String OWNER_ROLE = "RESTAURANT_OWNER";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(OwnerIdentity.class);
    }

    @Override
    public OwnerIdentity resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                         NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        if (!OWNER_ROLE.equals(webRequest.getHeader(HEADER_USER_ROLE))) {
            throw new OwnerOnlyException();
        }
        return new OwnerIdentity(userId(webRequest.getHeader(HEADER_USER_ID)));
    }

    private static Long userId(String header) {
        if (header == null) {
            throw new OwnerOnlyException();
        }
        try {
            return Long.valueOf(header);
        } catch (NumberFormatException e) {
            throw new OwnerOnlyException();
        }
    }
}
