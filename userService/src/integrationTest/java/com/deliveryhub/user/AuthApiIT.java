package com.deliveryhub.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class AuthApiIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void registersUserAndLogsIn() {
        MvcTestResult registered = postJson("/api/auth/register", """
                {"email": "kate@example.com", "password": "secret123", "name": "Kate", "phone": "+79991234567"}
                """);

        assertThat(registered).hasStatus(201).bodyJson().extractingPath("$.role").isEqualTo("USER");
        assertThat(registered).bodyText().doesNotContain("secret123").doesNotContain("password");

        MvcTestResult loggedIn = postJson("/api/auth/login", """
                {"email": "kate@example.com", "password": "secret123"}
                """);

        assertThat(loggedIn).hasStatusOk().bodyJson().extractingPath("$.type").isEqualTo("Bearer");
        assertThat(loggedIn).bodyJson().extractingPath("$.token").asString().isNotBlank();
    }

    @Test
    void rejectsSecondRegistrationWithSameEmail() {
        String body = """
                {"email": "twice@example.com", "password": "secret123", "name": "Twice"}
                """;
        postJson("/api/auth/register", body);

        assertThat(postJson("/api/auth/register", body)).hasStatus(409);
    }

    @Test
    void rejectsWrongPassword() {
        postJson("/api/auth/register", """
                {"email": "max@example.com", "password": "secret123", "name": "Max"}
                """);

        MvcTestResult result = postJson("/api/auth/login", """
                {"email": "max@example.com", "password": "wrong-password"}
                """);

        assertThat(result).hasStatus(401)
                .bodyJson().extractingPath("$.detail").isEqualTo("Invalid email or password");
    }

    @Test
    void validatesRegistrationRequest() {
        MvcTestResult result = postJson("/api/auth/register", """
                {"email": "not-an-email", "password": "short", "name": ""}
                """);

        assertThat(result).hasStatus(400).bodyJson().extractingPath("$.errors").asMap()
                .containsKeys("email", "password", "name");
    }

    private MvcTestResult postJson(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
