package com.deliveryhub.user;

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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class})
class FavoriteRestaurantApiIT {

    private static final String BASE = "/api/users/me/favorites";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void addingTwiceKeepsOneEntryAndNewestComesFirst() {
        int userId = register();

        assertThat(mvc.put().uri(BASE + "/5").header("X-User-Id", userId).exchange()).hasStatus(204);
        assertThat(mvc.put().uri(BASE + "/5").header("X-User-Id", userId).exchange()).hasStatus(204);
        assertThat(mvc.put().uri(BASE + "/9").header("X-User-Id", userId).exchange()).hasStatus(204);

        assertThat(list(userId)).containsExactly(9, 5);
    }

    @Test
    void removingIsIdempotentAndFavoritesAreSeparatedPerUser() {
        int first = register();
        int second = register();
        mvc.put().uri(BASE + "/5").header("X-User-Id", first).exchange();
        mvc.put().uri(BASE + "/6").header("X-User-Id", second).exchange();

        assertThat(mvc.delete().uri(BASE + "/5").header("X-User-Id", first).exchange()).hasStatus(204);
        assertThat(mvc.delete().uri(BASE + "/5").header("X-User-Id", first).exchange()).hasStatus(204);

        assertThat(list(first)).isEmpty();
        assertThat(list(second)).containsExactly(6);
    }

    private List<Integer> list(int userId) {
        MvcTestResult result = mvc.get().uri(BASE).header("X-User-Id", userId).exchange();
        assertThat(result).hasStatusOk();
        return JsonPath.read(bodyOf(result), "$");
    }

    private int register() {
        MvcTestResult registered = mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + UUID.randomUUID() + "@example.com\", \"password\": \"secret123\", \"name\": \"Test\"}")
                .exchange();
        assertThat(registered).hasStatus(201);
        return JsonPath.read(bodyOf(registered), "$.id");
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
