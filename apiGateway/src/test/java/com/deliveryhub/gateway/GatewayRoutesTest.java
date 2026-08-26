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
                "auth-login", "restaurant-service", "catalog-import", "restaurant-owners", "restaurant-cabinet",
                "user-admin", "order-service", "cart", "promo-admin", "delivery-service", "courier-service",
                "courier-admin", "payment-service", "notification-service", "search-service", "user-service", "user-profile",
                "restaurant-docs", "order-docs", "delivery-docs", "payment-docs", "notification-docs", "search-docs", "user-docs", "delivery-tracking", "cabinet-analytics", "admin-analytics");
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
    void cabinetAndOwnerAssignmentGoToRestaurantServiceAndUserRolesToUserService() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).filteredOn(route -> List.of("restaurant-cabinet", "restaurant-owners")
                        .contains(route.getId()))
                .hasSize(2)
                .allSatisfy(route -> assertThat(route.getUri().getHost()).isEqualTo("restaurantService"));
        assertThat(routes).filteredOn(route -> route.getId().equals("user-admin"))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().getHost()).isEqualTo("userService"));
    }

    @Test
    void trackingSocketGoesToDeliveryServiceOverWebSocket() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).filteredOn(route -> route.getId().equals("delivery-tracking"))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri()).hasToString("lb:ws://deliveryService"));
    }

    @Test
    void courierRoutesGoToDeliveryService() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).filteredOn(route -> List.of("courier-service", "courier-admin").contains(route.getId()))
                .hasSize(2)
                .allSatisfy(route -> assertThat(route.getUri().getHost()).isEqualTo("deliveryService"));
    }

    @Test
    void notificationsAndTheirDocsGoToNotificationService() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).filteredOn(route -> List.of("notification-service", "notification-docs")
                        .contains(route.getId()))
                .hasSize(2)
                .allSatisfy(route -> assertThat(route.getUri().getHost()).isEqualTo("notificationService"));
    }

    @Test
    void paymentsAndTheirDocsGoToPaymentService() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).filteredOn(route -> List.of("payment-service", "payment-docs").contains(route.getId()))
                .hasSize(2)
                .allSatisfy(route -> assertThat(route.getUri().getHost()).isEqualTo("paymentService"));
    }

    @Test
    void loginRouteIsCheckedBeforeGenericAuthRoute() {
        List<String> routeIds = routeLocator.getRoutes().map(Route::getId).collectList().block();

        assertThat(routeIds.indexOf("auth-login")).isLessThan(routeIds.indexOf("user-service"));
    }
}
