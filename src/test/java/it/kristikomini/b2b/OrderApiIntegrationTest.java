package it.kristikomini.b2b;

import com.fasterxml.jackson.databind.JsonNode;
import it.kristikomini.b2b.audit.AuditLog;
import it.kristikomini.b2b.audit.AuditLogRepository;
import it.kristikomini.b2b.order.Order;
import it.kristikomini.b2b.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API against real Postgres + Redis. Authentication is supplied by Spring Security's
 * {@code jwt()} post-processor (a signed token with a {@code tenant} claim), so this suite
 * exercises the whole stack — idempotency, audit, keyset paging, tenant isolation — without
 * needing a live Keycloak (that is covered separately in {@code KeycloakAuthIntegrationTest}).
 *
 * <p>Needs Docker, so it is skipped locally and runs in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(OrderApiIntegrationTest.StubJwtDecoder.class)
class OrderApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("b2b").withUsername("app").withPassword("app");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /** With the {@code jwt()} post-processor the decoder is never invoked; this satisfies the
     * resource-server wiring so the context starts without reaching a real Keycloak. */
    @TestConfiguration
    static class StubJwtDecoder {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("decoding not used under jwt() post-processor");
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired OrderRepository orderRepository;
    @Autowired AuditLogRepository auditRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static RequestPostProcessor asTenant(String tenant) {
        return jwt().jwt(builder -> builder.claim("tenant", tenant));
    }

    @BeforeEach
    void clean() {
        orderRepository.deleteAll();
        auditRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void duplicatePostWithSameIdempotencyKeyCreatesOneOrder() throws Exception {
        String body = """
                {"customerRef":"CUST-1","amount":99.90,"description":"widgets"}""";

        String first = mvc.perform(post("/orders").with(asTenant("acme"))
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(post("/orders").with(asTenant("acme"))
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long firstId = objectMapper.readTree(first).get("id").asLong();
        long secondId = objectMapper.readTree(second).get("id").asLong();
        assertThat(secondId).isEqualTo(firstId);          // same response returned from cache
        assertThat(orderRepository.count()).isEqualTo(1);  // handler executed exactly once
    }

    @Test
    void statusChangeIsAuditedAsynchronouslyWithTraceId() throws Exception {
        Order saved = orderRepository.save(new Order("acme", "CUST-2", new BigDecimal("10.00"), "to confirm"));

        mvc.perform(patch("/orders/{id}/status", saved.getId()).with(asTenant("acme"))
                        .header("X-Request-Id", "trace-abc")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"CONFIRMED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        AuditLog row = awaitOneAudit("Order", saved.getId().toString());
        assertThat(row.getChanges()).contains("status").contains("PENDING").contains("CONFIRMED");
        assertThat(row.getTraceId()).isEqualTo("trace-abc"); // traceId survived the async hop
        assertThat(row.getTenant()).isEqualTo("acme");
    }

    @Test
    void keysetPaginationReturnsEveryRowExactlyOnce() throws Exception {
        for (int i = 0; i < 5; i++) {
            orderRepository.save(new Order("acme", "CUST-" + i, new BigDecimal("1.00"), "o" + i));
        }

        Set<Integer> seen = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            String url = "/orders?limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            String json = mvc.perform(get(url).with(asTenant("acme")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = objectMapper.readTree(json);
            node.get("items").forEach(item -> seen.add(item.get("id").asInt()));
            cursor = node.get("nextCursor").isNull() ? null : node.get("nextCursor").asText();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(seen).hasSize(5); // no gaps, no duplicates across pages
    }

    @Test
    void aTenantCannotSeeOrChangeAnotherTenantsOrder() throws Exception {
        Order acmeOrder = orderRepository.save(new Order("acme", "CUST-9", new BigDecimal("5.00"), "acme only"));

        // globex sees an empty result set...
        mvc.perform(get("/orders").with(asTenant("globex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        // ...and cannot mutate acme's order (looks like a 404 to the wrong tenant).
        mvc.perform(patch("/orders/{id}/status", acmeOrder.getId()).with(asTenant("globex"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"CONFIRMED"}"""))
                .andExpect(status().isNotFound());
    }

    private AuditLog awaitOneAudit(String entity, String id) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            var rows = auditRepository.findByEntityNameAndEntityId(entity, id);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no audit row appeared for " + entity + "#" + id);
    }
}
