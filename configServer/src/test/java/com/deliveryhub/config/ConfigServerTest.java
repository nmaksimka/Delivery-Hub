package com.deliveryhub.config;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.server.native.search-locations=file:../config-repo",
                "spring.security.user.password=test-config-password"})
class ConfigServerTest {

    @Value("${local.server.port}")
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"apiGateway", "userService", "restaurantService", "orderService", "deliveryService"})
    void servesSharedAndOwnSettingsToEveryService(String service) {
        List<String> sources = JsonPath.read(config(service, "default"), "$.propertySources[*].name");

        assertThat(sources).hasSize(2);
        assertThat(sources.getFirst()).endsWith("config-repo/" + service + ".yml");
        assertThat(sources.getLast()).endsWith("config-repo/application.yml");
    }

    @Test
    void dockerProfileOverridesAddressesOfInfrastructure() {
        String config = config("orderService", "docker");

        List<String> sources = JsonPath.read(config, "$.propertySources[*].name");
        assertThat(sources).hasSize(4);
        assertThat(sources.getFirst()).endsWith("config-repo/orderService-docker.yml");

        String datasourceUrl = JsonPath.read(config, "$.propertySources[0].source['spring.datasource.url']");
        assertThat(datasourceUrl).isEqualTo("jdbc:postgresql://postgres-order:5432/order_db");

        List<String> eurekaZones = JsonPath.read(config,
                "$.propertySources[?(@.name =~ /.*application-docker.yml/)].source['eureka.client.service-url.defaultZone']");
        assertThat(eurekaZones).singleElement().asString()
                .endsWith("@discoveryServer:8761/eureka/")
                .contains("${EUREKA_PASSWORD:");
    }

    @Test
    void outboxSettingsLiveInTheRepository() {
        Integer batchSize = JsonPath.read(config("deliveryService", "default"),
                "$.propertySources[0].source['app.outbox.batch-size']");

        assertThat(batchSize).isEqualTo(50);
    }

    @Test
    void refusesToServeSettingsWithoutCredentials() {
        RestClient anonymous = RestClient.create("http://localhost:" + port);

        assertThatThrownBy(() -> anonymous.get().uri("/orderService/docker").retrieve().body(String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThat(anonymous.get().uri("/actuator/health/readiness").retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(200);
    }

    private String config(String service, String profile) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("config", "test-config-password"))
                .build()
                .get()
                .uri("/{service}/{profile}", service, profile)
                .retrieve()
                .body(String.class);
    }
}
