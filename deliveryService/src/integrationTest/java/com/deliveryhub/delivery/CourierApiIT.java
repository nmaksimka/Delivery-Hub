package com.deliveryhub.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class})
class CourierApiIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void registeredCourierGoesOnlineAndReportsPosition() {
        assertThat(mvc.post().uri("/api/admin/couriers")
                .header("X-User-Id", "1").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": 6001, \"name\": \"Олег\", \"phone\": \"+79990006001\"}"))
                .hasStatus(201)
                .bodyJson().extractingPath("$.status").isEqualTo("OFFLINE");

        assertThat(asCourier(mvc.put().uri("/api/couriers/me/status"), 6001)
                .content("{\"status\": \"AVAILABLE\"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("AVAILABLE");
        assertThat(asCourier(mvc.put().uri("/api/couriers/me/location"), 6001)
                .content("{\"latitude\": 56.4884, \"longitude\": 84.948}"))
                .hasStatus(204);

        assertThat(mvc.get().uri("/api/admin/couriers?status=AVAILABLE&size=100")
                .header("X-User-Id", "1").header("X-User-Role", "ADMIN"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.content[?(@.userId == 6001)].latitude").asArray().containsExactly(56.4884);
    }

    @Test
    void secondProfileForSameUserIsConflict() {
        String body = "{\"userId\": 6002, \"name\": \"Олег\", \"phone\": \"+79990006002\"}";
        mvc.post().uri("/api/admin/couriers").header("X-User-Id", "1").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(mvc.post().uri("/api/admin/couriers").header("X-User-Id", "1").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(409);
    }

    @Test
    void rejectsInvalidPositionAndForeignRoles() {
        assertThat(asCourier(mvc.put().uri("/api/couriers/me/location"), 6003)
                .content("{\"latitude\": 95, \"longitude\": 84.9}"))
                .hasStatus(400);
        assertThat(asCourier(mvc.put().uri("/api/couriers/me/status"), 6003)
                .content("{\"status\": \"AVAILABLE\"}"))
                .hasStatus(404);
        assertThat(mvc.put().uri("/api/couriers/me/status").header("X-User-Id", "6003")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"AVAILABLE\"}"))
                .hasStatus(403);
        assertThat(mvc.get().uri("/api/admin/couriers").header("X-User-Id", "6003").header("X-User-Role", "COURIER"))
                .hasStatus(403);
    }

    private static MockMvcTester.MockMvcRequestBuilder asCourier(MockMvcTester.MockMvcRequestBuilder request,
                                                                 long userId) {
        return request.header("X-User-Id", Long.toString(userId)).header("X-User-Role", "COURIER")
                .contentType(MediaType.APPLICATION_JSON);
    }
}
