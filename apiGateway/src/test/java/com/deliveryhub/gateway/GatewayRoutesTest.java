package com.deliveryhub.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void loadsRoutesForEveryServiceAndItsApiDocs() {
        List<String> routeIds = routeLocator.getRoutes().map(Route::getId).collectList().block();

        assertThat(routeIds).containsExactlyInAnyOrder(
                "restaurant-service", "order-service", "delivery-service", "user-service",
                "restaurant-docs", "order-docs", "delivery-docs", "user-docs");
    }
}
