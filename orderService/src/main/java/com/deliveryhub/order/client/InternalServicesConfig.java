package com.deliveryhub.order.client;

import com.deliveryhub.order.security.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(InternalServicesProperties.class)
public class InternalServicesConfig {

    private final InternalServicesProperties properties;
    private final String internalToken;

    public InternalServicesConfig(InternalServicesProperties properties,
                                  @Value("${app.internal-token}") String internalToken) {
        this.properties = properties;
        this.internalToken = internalToken;
    }

    @Bean
    public UserAddresses userAddresses(RestClient.Builder builder) {
        return new UserAddresses(client(builder, properties.usersBaseUrl()));
    }

    @Bean
    public DeliveryQuotes deliveryQuotes(RestClient.Builder builder) {
        return new DeliveryQuotes(client(builder, properties.deliveryBaseUrl()));
    }

    private RestClient client(RestClient.Builder builder, String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalTokenFilter.HEADER_INTERNAL_TOKEN, internalToken)
                .build();
    }
}
