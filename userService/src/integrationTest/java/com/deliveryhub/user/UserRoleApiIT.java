package com.deliveryhub.user;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class UserRoleApiIT {

    private static final String ADMIN_ID = "1000000";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void adminMakesUserRestaurantOwnerAndNextTokensCarryNewRole() throws ParseException {
        String email = UUID.randomUUID() + "@example.com";
        int userId = register(email);
        String refreshToken = JsonPath.read(bodyOf(login(email)), "$.refreshToken");

        MvcTestResult changed = changeRole(userId, "RESTAURANT_OWNER", "ADMIN");

        assertThat(changed).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("RESTAURANT_OWNER");
        assertThat(roleInToken(login(email))).isEqualTo("RESTAURANT_OWNER");
        assertThat(roleInToken(postJson("/api/auth/refresh", "{\"refreshToken\": \"" + refreshToken + "\"}")))
                .isEqualTo("RESTAURANT_OWNER");
    }

    @Test
    void adminFindsUserByEmail() {
        String email = UUID.randomUUID() + "@example.com";
        int userId = register(email);

        MvcTestResult found = mvc.get().uri("/api/admin/users?email={email}", email.toUpperCase())
                .header("X-User-Id", ADMIN_ID).header("X-User-Role", "ADMIN").exchange();

        assertThat(found).hasStatusOk().bodyJson().extractingPath("$.id").isEqualTo(userId);
        assertThat(found).bodyText().doesNotContain("password");
    }

    @Test
    void roleChangeIsForbiddenWithoutAdminRole() {
        int userId = register(UUID.randomUUID() + "@example.com");

        assertThat(changeRole(userId, "RESTAURANT_OWNER", "USER")).hasStatus(403);
        assertThat(changeRole(userId, "RESTAURANT_OWNER", "RESTAURANT_OWNER")).hasStatus(403);
        assertThat(mvc.put().uri("/api/admin/users/{id}/role", userId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"ADMIN\"}").exchange()).hasStatus(403);
        assertThat(mvc.get().uri("/api/admin/users?email=a@example.com").exchange()).hasStatus(403);
    }

    @Test
    void rejectsUnknownUserUnknownRoleAndOwnRole() {
        int userId = register(UUID.randomUUID() + "@example.com");

        assertThat(changeRole(999_999, "COURIER", "ADMIN")).hasStatus(404);
        assertThat(changeRole(userId, "SUPERUSER", "ADMIN")).hasStatus(400);
        assertThat(mvc.put().uri("/api/admin/users/{id}/role", userId)
                .header("X-User-Id", ADMIN_ID).header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange()).hasStatus(400);
        assertThat(changeRole(Integer.parseInt(ADMIN_ID), "USER", "ADMIN")).hasStatus(409);
    }

    private MvcTestResult changeRole(int userId, String newRole, String callerRole) {
        return mvc.put().uri("/api/admin/users/{id}/role", userId)
                .header("X-User-Id", ADMIN_ID).header("X-User-Role", callerRole)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"" + newRole + "\"}").exchange();
    }

    private int register(String email) {
        MvcTestResult registered = postJson("/api/auth/register",
                "{\"email\": \"" + email + "\", \"password\": \"secret123\", \"name\": \"Test\"}");
        assertThat(registered).hasStatus(201);
        return JsonPath.read(bodyOf(registered), "$.id");
    }

    private MvcTestResult login(String email) {
        return postJson("/api/auth/login", "{\"email\": \"" + email + "\", \"password\": \"secret123\"}");
    }

    private static String roleInToken(MvcTestResult tokens) throws ParseException {
        assertThat(tokens).hasStatusOk();
        String accessToken = JsonPath.read(bodyOf(tokens), "$.accessToken");
        return SignedJWT.parse(accessToken).getJWTClaimsSet().getStringClaim("role");
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
