package com.deliveryhub.notification.infrastructure.users;

import com.deliveryhub.notification.infrastructure.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class UsersClientConfig {

    @Bean
    public UserDirectory userDirectory(RestClient.Builder builder, UsersProperties properties,
                                       @Value("${app.internal-token}") String internalToken) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new UserDirectory(builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(InternalTokenFilter.HEADER_INTERNAL_TOKEN, internalToken)
                .build());
    }
}
