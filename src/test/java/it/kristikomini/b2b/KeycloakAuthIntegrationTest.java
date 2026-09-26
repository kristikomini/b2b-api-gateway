package it.kristikomini.b2b;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real auth path end to end: a genuine <b>Keycloak</b> issues a token (with the
 * {@code tenant} claim from the user's attributes), and the service validates it as a resource
 * server — no mocked decoder. This is the difference between "JWT security works in a unit test"
 * and "JWT security works against a real IdP".
 *
 * <p>Needs Docker; skipped locally, runs in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class KeycloakAuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("b2b").withUsername("app").withPassword("app");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer()
            .withRealmImportFile("keycloak/realm-export.json");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // The real JwtDecoder is built from this issuer (JWKS fetched from the running Keycloak).
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> keycloak.getAuthServerUrl() + "/realms/b2b");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void acceptsARealKeycloakTokenAndDerivesTheTenantFromItsClaim() throws Exception {
        String token = obtainAccessToken();

        mvc.perform(post("/orders").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "kc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerRef":"CUST-KC","amount":42.00,"description":"via keycloak"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenant").value("acme")); // tenant came from the token, not the request
    }

    @Test
    void rejectsAnUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    /** Password grant against the real Keycloak to get a signed access token for the test user. */
    private String obtainAccessToken() throws Exception {
        String form = "grant_type=password"
                + "&client_id=b2b-api"
                + "&username=acme-user"
                + "&password=" + URLEncoder.encode("password", StandardCharsets.UTF_8);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(keycloak.getAuthServerUrl() + "/realms/b2b/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        return objectMapper.readTree(response.body()).get("access_token").asText();
    }
}
