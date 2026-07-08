package com.deliveryhub.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.security.user.password=test-eureka-password")
class DiscoveryServerTest {

    private static final String INSTANCE = """
            {"instance": {
                "instanceId": "orderService:3332",
                "hostName": "localhost",
                "app": "ORDERSERVICE",
                "ipAddr": "127.0.0.1",
                "vipAddress": "orderService",
                "status": "UP",
                "port": {"$": 3332, "@enabled": "true"},
                "dataCenterInfo": {
                    "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                    "name": "MyOwn"}}}
            """;

    @Value("${local.server.port}")
    private int port;

    @Test
    void startsWithEmptyRegistry() {
        ResponseEntity<String> apps = client().get().uri("/eureka/apps")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(String.class);

        assertThat(apps.getStatusCode().value()).isEqualTo(200);
        assertThat(apps.getBody()).contains("\"applications\"").doesNotContain("ORDERSERVICE");
    }

    @Test
    void registeredInstanceBecomesVisibleToOtherClients() {
        ResponseEntity<Void> registered = client().post().uri("/eureka/apps/ORDERSERVICE")
                .contentType(MediaType.APPLICATION_JSON)
                .body(INSTANCE)
                .retrieve()
                .toBodilessEntity();

        assertThat(registered.getStatusCode().value()).isEqualTo(204);

        String instance = client().get().uri("/eureka/apps/ORDERSERVICE/orderService:3332")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        assertThat(instance).contains("\"vipAddress\":\"orderService\"").contains("\"status\":\"UP\"");
    }

    @Test
    void rejectsRegistryAccessWithoutCredentials() {
        assertThatThrownBy(() -> anonymous().get().uri("/eureka/apps").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThatThrownBy(() -> anonymous().post().uri("/eureka/apps/ORDERSERVICE")
                .contentType(MediaType.APPLICATION_JSON)
                .body(INSTANCE)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void rejectsWrongPassword() {
        RestClient intruder = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("eureka", "guess"))
                .build();

        assertThatThrownBy(() -> intruder.get().uri("/eureka/apps").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void healthProbeStaysOpenForDockerHealthcheck() {
        ResponseEntity<String> health = anonymous().get().uri("/actuator/health/readiness")
                .retrieve()
                .toEntity(String.class);

        assertThat(health.getStatusCode().value()).isEqualTo(200);
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("eureka", "test-eureka-password"))
                .build();
    }

    private RestClient anonymous() {
        return RestClient.create("http://localhost:" + port);
    }
}
