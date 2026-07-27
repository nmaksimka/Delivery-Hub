package com.deliveryhub.restaurant;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class})
class RestaurantApiIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void createsRestaurantWithMenuAndReadsItBack() {
        String restaurantUri = "/api/restaurants/" + createRestaurant("Trattoria");

        MvcTestResult created = postJson(restaurantUri + "/menu", """
                {"name": "Carbonara", "price": 590.00, "category": "pasta"}
                """);

        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.available").isEqualTo(true);
        assertThat(mvc.get().uri(restaurantUri + "/menu")).hasStatusOk()
                .bodyJson().extractingPath("$[0].name").isEqualTo("Carbonara");
        assertThat(mvc.get().uri(restaurantUri)).hasStatusOk()
                .bodyJson().extractingPath("$.active").isEqualTo(true);
    }

    @Test
    void returnsRequestedMenuItemsInOneCallIncludingUnavailable() {
        String restaurantUri = "/api/restaurants/" + createRestaurant("Batch");
        Integer soup = JsonPath.read(bodyOf(postJson(restaurantUri + "/menu", """
                {"name": "Soup", "price": 250.00}
                """)), "$.id");
        Integer stopped = JsonPath.read(bodyOf(postJson(restaurantUri + "/menu", """
                {"name": "Stopped", "price": 300.00, "available": false}
                """)), "$.id");
        postJson(restaurantUri + "/menu", """
                {"name": "Other", "price": 100.00}
                """);

        assertThat(mvc.get().uri(restaurantUri + "/menu?ids=" + soup + "," + stopped + ",999999")).hasStatusOk()
                .bodyJson().extractingPath("$[*].name").asArray().containsExactlyInAnyOrder("Soup", "Stopped");
    }

    @Test
    void rejectsMenuItemWithoutPrice() {
        String restaurantUri = "/api/restaurants/" + createRestaurant("Bistro");

        MvcTestResult result = postJson(restaurantUri + "/menu", """
                {"name": "Soup"}
                """);

        assertThat(result).hasStatus(400)
                .bodyJson().extractingPath("$.errors.price").isEqualTo("Price is required");
    }

    @Test
    void keepsStandardStatusesForMalformedRequests() {
        assertThat(mvc.post().uri("/api/restaurants").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .hasStatus(400).bodyJson().extractingPath("$.title").isEqualTo("Bad Request");
        assertThat(mvc.patch().uri("/api/restaurants")).hasStatus(405);
        assertThat(mvc.post().uri("/api/restaurants").header("X-User-Role", "ADMIN")
                .contentType(MediaType.TEXT_PLAIN).content("pizza"))
                .hasStatus(415);
        assertThat(mvc.get().uri("/api/restaurants/not-a-number")).hasStatus(400);
    }

    @Test
    void onlyAdminChangesCatalogAndReachesAdminApi() {
        String json = "{\"name\": \"Sneaky\"}";

        assertThat(mvc.post().uri("/api/restaurants").contentType(MediaType.APPLICATION_JSON).content(json))
                .hasStatus(403).bodyJson().extractingPath("$.detail").asString().contains("administrators");
        assertThat(mvc.post().uri("/api/restaurants").header("X-User-Role", "RESTAURANT_OWNER")
                .contentType(MediaType.APPLICATION_JSON).content(json)).hasStatus(403);
        assertThat(mvc.delete().uri("/api/restaurants/1").header("X-User-Role", "USER")).hasStatus(403);
        assertThat(mvc.get().uri("/api/admin/imports").header("X-User-Role", "COURIER")).hasStatus(403);
        assertThat(mvc.get().uri("/api/restaurants")).hasStatusOk();
    }

    @Test
    void returnsProblemDetailForUnknownRestaurant() {
        assertThat(mvc.get().uri("/api/restaurants/999999")).hasStatus(404)
                .bodyJson().extractingPath("$.detail").asString().contains("999999");
    }

    @Test
    void hidesInactiveRestaurantsByDefault() {
        postJson("/api/restaurants", """
                {"name": "Hidden place", "active": false}
                """);

        assertThat(mvc.get().uri("/api/restaurants")).hasStatusOk()
                .bodyText().doesNotContain("Hidden place");
        assertThat(mvc.get().uri("/api/restaurants?activeOnly=false")).hasStatusOk()
                .bodyText().contains("Hidden place");
    }

    private Integer createRestaurant(String name) {
        MvcTestResult result = postJson("/api/restaurants", """
                {"name": "%s", "address": "Lenina 1"}
                """.formatted(name));
        assertThat(result).hasStatus(201);
        return JsonPath.read(bodyOf(result), "$.id");
    }

    private MvcTestResult postJson(String uri, String json) {
        return mvc.post().uri(uri).header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
