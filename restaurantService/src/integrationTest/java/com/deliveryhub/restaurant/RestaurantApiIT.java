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
@Import(PostgresContainerConfig.class)
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
    void rejectsMenuItemWithoutPrice() {
        String restaurantUri = "/api/restaurants/" + createRestaurant("Bistro");

        MvcTestResult result = postJson(restaurantUri + "/menu", """
                {"name": "Soup"}
                """);

        assertThat(result).hasStatus(400)
                .bodyJson().extractingPath("$.errors.price").isEqualTo("Price is required");
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
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
