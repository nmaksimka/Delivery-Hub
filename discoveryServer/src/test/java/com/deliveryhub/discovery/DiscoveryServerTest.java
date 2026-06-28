package com.deliveryhub.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}
