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
                "auth-login", "restaurant-service", "order-service", "delivery-service", "user-service",
                "restaurant-docs", "order-docs", "delivery-docs", "user-docs");
    }

    @Test
    void everyRouteGoesThroughLoadBalancerByServiceName() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).allSatisfy(route -> assertThat(route.getUri().getScheme()).isEqualTo("lb"));
        assertThat(routes).filteredOn(route -> route.getId().equals("order-service"))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().getHost()).isEqualTo("orderService"));
    }

    @Test
    void loginRouteIsCheckedBeforeGenericAuthRoute() {
        List<String> routeIds = routeLocator.getRoutes().map(Route::getId).collectList().block();

        assertThat(routeIds.indexOf("auth-login")).isLessThan(routeIds.indexOf("user-service"));
    }
}
