package com.deliveryhub.restaurant.config;

import com.deliveryhub.restaurant.security.AdminOnlyInterceptor;
import com.deliveryhub.restaurant.security.OwnerIdentity;
import com.deliveryhub.restaurant.security.OwnerIdentityResolver;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(OwnerIdentity.class);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminOnlyInterceptor())
                .addPathPatterns(AdminOnlyInterceptor.ADMIN_PATHS, AdminOnlyInterceptor.CATALOG_PATHS);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new OwnerIdentityResolver());
    }
}
