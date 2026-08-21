package com.deliveryhub.user;

import com.deliveryhub.user.service.GeocodingRetry;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class})
class AddressApiIT {

    private static final String BASE = "/api/users/me/addresses";
    private static final WireMockServer NOMINATIM = new WireMockServer(options().dynamicPort());
    private static final String FOUND = "[{\"lat\": \"56.4846453\", \"lon\": \"84.9476493\"}]";

    static {
        NOMINATIM.start();
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private GeocodingRetry geocodingRetry;

    @DynamicPropertySource
    static void nominatimIsWireMock(DynamicPropertyRegistry registry) {
        registry.add("app.geocoding.base-url", NOMINATIM::baseUrl);
        registry.add("app.geocoding.requests-per-second", () -> 100);
        registry.add("app.geocoding.cache-size", () -> 0);
    }

    @AfterAll
    static void stopNominatim() {
        NOMINATIM.stop();
    }

    @BeforeEach
    void stubNominatim() {
        NOMINATIM.resetAll();
        NOMINATIM.stubFor(WireMock.get(urlPathEqualTo("/search")).willReturn(okJson(FOUND)));
    }

    @Test
    void createdAddressGetsCoordinatesFromNominatim() {
        int userId = register();

        MvcTestResult created = post(userId, BASE, addressJson("Дом", "Томск"));

        assertThat(created).hasStatus(201);
        assertThat(created).bodyJson().extractingPath("$.geocoded").isEqualTo(true);
        assertThat(created).bodyJson().extractingPath("$.latitude").isEqualTo(56.484645);
        assertThat(created).bodyJson().extractingPath("$.longitude").isEqualTo(84.947649);
    }

    @Test
    void rejectsAddressThatIsNotOnMap() {
        NOMINATIM.stubFor(WireMock.get(urlPathEqualTo("/search")).withQueryParam("street", equalTo("12 Ленина"))
                .willReturn(okJson("[]")));
        int userId = register();

        MvcTestResult rejected = post(userId, BASE, addressJson("Дом", "Томск"));

        assertThat(rejected).hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("ADDRESS_NOT_FOUND_ON_MAP");
        assertThat(ids(get(userId))).isEmpty();
    }

    @Test
    void keepsAddressWithoutCoordinatesWhileNominatimIsDownAndLocatesItLater() {
        NOMINATIM.stubFor(WireMock.get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(503)));
        int userId = register();

        MvcTestResult saved = post(userId, BASE, addressJson("Дом", "Томск"));

        assertThat(saved).hasStatus(201).bodyJson().extractingPath("$.geocoded").isEqualTo(false);
        NOMINATIM.resetAll();
        NOMINATIM.stubFor(WireMock.get(urlPathEqualTo("/search")).willReturn(okJson(FOUND)));
        geocodingRetry.retryPending();
        MvcTestResult list = get(userId);
        assertThat(list).bodyJson().extractingPath("$[0].geocoded").isEqualTo(true);
    }

    @Test
    void internalEndpointGivesAddressOnlyWithInternalToken() {
        int userId = register();
        int address = createAddress(userId, "Дом");

        MvcTestResult internal = mvc.get().uri("/internal/users/{userId}/addresses/{id}", userId, address).exchange();
        MvcTestResult foreign = mvc.get().uri("/internal/users/{userId}/addresses/{id}", userId + 1000, address).exchange();
        MvcTestResult anonymous = mvc.get().uri("/internal/users/{userId}/addresses/{id}", userId, address)
                .header("X-Internal-Token", "wrong").exchange();

        assertThat(internal).hasStatusOk().bodyJson().extractingPath("$.street").isEqualTo("Ленина");
        assertThat(foreign).hasStatus(404);
        assertThat(anonymous).hasStatus(401);
    }

    @Test
    void firstAddressIsPrimaryAndListPutsPrimaryFirst() {
        int userId = register();
        int home = createAddress(userId, "Дом");
        int work = createAddress(userId, "Работа");

        MvcTestResult list = get(userId);

        assertThat(list).hasStatusOk();
        assertThat(ids(list)).containsExactly(home, work);
        assertThat(primaries(list)).containsExactly(true, false);
    }

    @Test
    void makingAnotherAddressPrimaryLeavesExactlyOnePrimary() {
        int userId = register();
        createAddress(userId, "Дом");
        int work = createAddress(userId, "Работа");

        MvcTestResult switched = post(userId, BASE + "/" + work + "/default", null);

        assertThat(switched).hasStatusOk().bodyJson().extractingPath("$.primary").isEqualTo(true);
        MvcTestResult list = get(userId);
        assertThat(ids(list).get(0)).isEqualTo(work);
        assertThat(primaries(list)).containsExactly(true, false);
    }

    @Test
    void deletingPrimaryPromotesOldestRemaining() {
        int userId = register();
        int home = createAddress(userId, "Дом");
        int work = createAddress(userId, "Работа");
        createAddress(userId, "Дача");

        assertThat(mvc.delete().uri(BASE + "/" + home).header("X-User-Id", userId).exchange()).hasStatus(204);

        MvcTestResult list = get(userId);
        assertThat(ids(list).get(0)).isEqualTo(work);
        assertThat(primaries(list)).containsExactly(true, false);
    }

    @Test
    void rejectsEleventhAddress() {
        int userId = register();
        for (int i = 0; i < 10; i++) {
            createAddress(userId, "Адрес " + i);
        }

        MvcTestResult rejected = post(userId, BASE, addressJson("Лишний", "Томск"));

        assertThat(rejected).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ADDRESS_LIMIT_REACHED");
    }

    @Test
    void rejectsCityOtherThanTomsk() {
        int userId = register();

        MvcTestResult rejected = post(userId, BASE, addressJson("Дом", "Москва"));

        assertThat(rejected).hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void foreignAddressIsNotFoundForEveryOperation() {
        int owner = register();
        int stranger = register();
        int address = createAddress(owner, "Дом");

        assertThat(put(stranger, BASE + "/" + address, addressJson("Взлом", "Томск"))).hasStatus(404)
                .bodyJson().extractingPath("$.code").isEqualTo("ADDRESS_NOT_FOUND");
        assertThat(mvc.delete().uri(BASE + "/" + address).header("X-User-Id", stranger).exchange()).hasStatus(404);
        assertThat(post(stranger, BASE + "/" + address + "/default", null)).hasStatus(404);
        assertThat(ids(get(owner))).containsExactly(address);
        assertThat(ids(get(stranger))).isEmpty();
    }

    @Test
    void updatesAddressFields() {
        int userId = register();
        int address = createAddress(userId, "Дом");

        MvcTestResult changed = put(userId, BASE + "/" + address, addressJson("Работа", "томск"));

        assertThat(changed).hasStatusOk();
        assertThat(changed).bodyJson().extractingPath("$.label").isEqualTo("Работа");
        assertThat(changed).bodyJson().extractingPath("$.city").isEqualTo("Томск");
    }

    private int createAddress(int userId, String label) {
        MvcTestResult created = post(userId, BASE, addressJson(label, "Томск"));
        assertThat(created).hasStatus(201);
        return JsonPath.read(bodyOf(created), "$.id");
    }

    private int register() {
        MvcTestResult registered = mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + UUID.randomUUID() + "@example.com\", \"password\": \"secret123\", \"name\": \"Test\"}")
                .exchange();
        assertThat(registered).hasStatus(201);
        return JsonPath.read(bodyOf(registered), "$.id");
    }

    private MvcTestResult get(int userId) {
        return mvc.get().uri(BASE).header("X-User-Id", userId).exchange();
    }

    private MvcTestResult post(int userId, String uri, String json) {
        var request = mvc.post().uri(uri).header("X-User-Id", userId);
        return json == null ? request.exchange() : request.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(int userId, String uri, String json) {
        return mvc.put().uri(uri).header("X-User-Id", userId).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String addressJson(String label, String city) {
        return "{\"label\": \"" + label + "\", \"city\": \"" + city + "\", \"street\": \"Ленина\", \"house\": \"12\","
                + " \"apartment\": \"4\", \"courierComment\": \"домофон не работает\"}";
    }

    private static List<Integer> ids(MvcTestResult result) {
        return JsonPath.read(bodyOf(result), "$[*].id");
    }

    private static List<Boolean> primaries(MvcTestResult result) {
        return JsonPath.read(bodyOf(result), "$[*].primary");
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
