package com.deliveryhub.order.client;

import com.deliveryhub.order.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.cloud.contract.stubrunner.junit.StubRunnerExtension;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantOwnershipContractTest {

    @RegisterExtension
    static StubRunnerExtension restaurantStubs = new StubRunnerExtension()
            .downloadStub("com.deliveryhub", "restaurantService", "+", "stubs")
            .stubsMode(StubRunnerProperties.StubsMode.CLASSPATH);

    private RestaurantOwnership ownership;

    @BeforeEach
    void setUp() {
        String baseUrl = restaurantStubs.findStubUrl("restaurantService").toString();
        InternalServicesConfig config = new InternalServicesConfig(new InternalServicesProperties(baseUrl, baseUrl,
                baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(1)), "internal-secret");
        ownership = config.restaurantOwnership(RestClient.builder());
    }

    @Test
    void ownerPassesCheckOfOwnRestaurant() {
        assertThatCode(() -> ownership.requireOwner(42L, 7L)).doesNotThrowAnyException();
    }

    @Test
    void foreignOwnerIsForbidden() {
        assertThatThrownBy(() -> ownership.requireOwner(43L, 7L)).isInstanceOf(ForbiddenException.class);
    }
}
