package com.deliveryhub.restaurant;

import com.deliveryhub.restaurant.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestConfiguration(proxyBeanMethods = false)
public class GatewayTokenConfig {

    @Bean
    MockMvcBuilderCustomizer requestsComeThroughGateway(@Value("${app.internal-token}") String internalToken) {
        return builder -> builder.defaultRequest(get("/").header(InternalTokenFilter.HEADER_INTERNAL_TOKEN, internalToken));
    }
}
