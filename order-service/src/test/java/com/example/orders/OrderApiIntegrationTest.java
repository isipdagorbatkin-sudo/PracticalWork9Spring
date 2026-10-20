package com.example.orders;

import com.example.orders.order.OrderRepository;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.location=classpath:/application-test.yml",
        "spring.datasource.url=jdbc:h2:mem:orders;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.application.name=order-service",
        "app.restaurant.read-timeout=500ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiIntegrationTest {
    private static final String REQUEST = """
            {"restaurantId":1,"customerName":"  Артём  ","deliveryAddress":" Москва, Ленина, 1 ",
             "items":[{"menuItemId":1,"quantity":2}]}
            """;
    private static final String QUOTE = """
            {"restaurantId":1,"restaurantName":"Пиццерия",
             "items":[{"menuItemId":1,"name":"Маргарита","quantity":2,"unitPrice":550.00,"lineTotal":1100.00}],
             "totalAmount":1100.00}
            """;
    private static final AtomicReference<StubResponse> RESPONSE = new AtomicReference<>();
    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();
    private static final AtomicReference<String> LAST_REQUEST = new AtomicReference<>();
    private static final AtomicReference<String> LAST_METHOD = new AtomicReference<>();
    private static final ExecutorService STUB_EXECUTOR = Executors.newCachedThreadPool();
    private static final HttpServer RESTAURANT = startRestaurant();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private OrderRepository repository;
    @Autowired
    private RestClient.Builder plainRestClientBuilder;
    @Autowired
    @LoadBalanced
    private RestClient.Builder discoveryRestClientBuilder;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.restaurant-service[0].uri",
                () -> "http://localhost:" + RESTAURANT.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        repository.deleteAll();
        RESPONSE.set(new StubResponse(200, QUOTE, 0));
        REQUEST_COUNT.set(0);
        LAST_REQUEST.set(null);
        LAST_METHOD.set(null);
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopRestaurant() {
        RESTAURANT.stop(0);
        STUB_EXECUTOR.shutdownNow();
    }

    @Test
    void createsOrderAndKeepsHistoricalPricesWhenMenuChanges() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerName").value("Артём"))
                .andExpect(jsonPath("$.deliveryAddress").value("Москва, Ленина, 1"))
                .andExpect(jsonPath("$.restaurantName").value("Пиццерия"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(550.00))
                .andExpect(jsonPath("$.items[0].lineTotal").value(1100.00))
                .andExpect(jsonPath("$.totalAmount").value(1100.00))
                .andExpect(jsonPath("$.createdAt").exists());
        Long firstOrderId = repository.findAll().get(0).getId();
        assertThat(LAST_METHOD.get()).isEqualTo("POST");
        assertThat(LAST_REQUEST.get()).contains("\"items\"", "\"menuItemId\":1", "\"quantity\":2")
                .doesNotContain("customerName", "deliveryAddress", "Артём");

        RESPONSE.set(new StubResponse(200, QUOTE.replace("550.00", "600.00").replace("1100.00", "1200.00"), 0));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(1200.00));
        mvc.perform(get("/api/orders/{id}", firstOrderId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(1100.00));
        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].totalAmount").value(1200.00))
                .andExpect(jsonPath("$[1].id").value(firstOrderId));
        assertThat(REQUEST_COUNT.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"restaurantId\":0,\"customerName\":\"А\",\"deliveryAddress\":\"Б\",\"items\":[{\"menuItemId\":1,\"quantity\":2}]}",
            "{\"restaurantId\":1,\"customerName\":\"  \",\"deliveryAddress\":\"Б\",\"items\":[{\"menuItemId\":1,\"quantity\":2}]}",
            "{\"restaurantId\":1,\"customerName\":\"А\",\"deliveryAddress\":\"  \",\"items\":[{\"menuItemId\":1,\"quantity\":2}]}",
            "{\"restaurantId\":1,\"customerName\":\"А\",\"deliveryAddress\":\"Б\",\"items\":[]}",
            "{\"restaurantId\":1,\"customerName\":\"А\",\"deliveryAddress\":\"Б\",\"items\":[null]}",
            "{\"restaurantId\":1,\"customerName\":\"А\",\"deliveryAddress\":\"Б\",\"items\":[{\"menuItemId\":1,\"quantity\":21}]}",
            "{\"restaurantId\":1,\"customerName\":\"А\",\"deliveryAddress\":\"Б\",\"items\":[{\"menuItemId\":1,\"quantity\":2.5}]}",
            "{\"restaurantId\":1,\"customerName\":\"А\",\"deliveryAddress\":\"Б\",\"items\":[{\"menuItemId\":1,\"quantity\":1},{\"menuItemId\":1,\"quantity\":2}]}",
            "{}", "broken json"
    })
    void invalidOrderDoesNotCallRestaurantOrSaveAnything(String request) throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(repository.count()).isZero();
        assertThat(REQUEST_COUNT.get()).isZero();
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        mvc.perform(get("/api/orders/99999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void infrastructureUsesPlainHttpWhileRestaurantClientUsesDiscovery() {
        String directResponse = plainRestClientBuilder.clone()
                .baseUrl("http://localhost:" + RESTAURANT.getAddress().getPort()).build()
                .post().uri("/internal/restaurants/1/quote")
                .contentType(MediaType.APPLICATION_JSON).body("{\"items\":[{\"menuItemId\":1,\"quantity\":2}]}")
                .retrieve().body(String.class);
        String discoveredResponse = discoveryRestClientBuilder.clone()
                .baseUrl("http://restaurant-service").build()
                .post().uri("/internal/restaurants/1/quote")
                .contentType(MediaType.APPLICATION_JSON).body("{\"items\":[{\"menuItemId\":1,\"quantity\":2}]}")
                .retrieve().body(String.class);

        assertThat(directResponse).isEqualTo(QUOTE);
        assertThat(discoveredResponse).isEqualTo(QUOTE);
        assertThat(REQUEST_COUNT.get()).isEqualTo(2);
    }

    @Test
    void unsupportedHttpRequestsKeepTheirClientErrorStatus() throws Exception {
        mvc.perform(delete("/api/orders/1"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("HTTP_ERROR"));
        mvc.perform(post("/api/orders").contentType(MediaType.TEXT_PLAIN).content(REQUEST))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("HTTP_ERROR"));
        mvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HTTP_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409})
    void businessErrorsKeepTheirStatus(int restaurantStatus) throws Exception {
        RESPONSE.set(new StubResponse(restaurantStatus, "{\"message\":\"do not leak internal error\"}", 0));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().is(restaurantStatus))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("do not leak internal error")));
        }
        assertThat(repository.count()).isZero();
        assertThat(REQUEST_COUNT.get()).isEqualTo(5);
    }

    @Test
    void timeoutReturns503WithoutSavingOrder() throws Exception {
        RESPONSE.set(new StubResponse(200, QUOTE, 1200));
        long start = System.nanoTime();
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RESTAURANT_UNAVAILABLE"));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        assertThat(repository.count()).isZero();
    }

    @Test
    void invalidQuoteReturns503WithoutSavingOrder() throws Exception {
        RESPONSE.set(new StubResponse(200, QUOTE.replace("\"totalAmount\":1100.00", "\"totalAmount\":1.00"), 0));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RESTAURANT_UNAVAILABLE"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void malformedResponseReturns503WithoutSavingOrder() throws Exception {
        RESPONSE.set(new StubResponse(200, "not json", 0));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable());
        assertThat(repository.count()).isZero();
    }

    @Test
    void circuitBreakerStopsCallsUntilItIsReset() throws Exception {
        RESPONSE.set(new StubResponse(500, "{}", 0));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isServiceUnavailable());
        }
        assertThat(REQUEST_COUNT.get()).isEqualTo(5);
        assertThat(repository.count()).isZero();
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable());
        assertThat(REQUEST_COUNT.get()).isEqualTo(5);
        assertThat(circuitBreakerRegistry.circuitBreaker("restaurantService").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        circuitBreakerRegistry.circuitBreaker("restaurantService").reset();
        RESPONSE.set(new StubResponse(200, QUOTE, 0));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void exposesOpenApiDocument() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Food Delivery — заказы"))
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"))
                .andExpect(jsonPath("$.paths['/api/orders'].post").exists());
    }

    @Test
    void exposesMonitoringEndpoints() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
        mvc.perform(get("/actuator/circuitbreakers"))
                .andExpect(status().isOk());
    }

    private static HttpServer startRestaurant() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(STUB_EXECUTOR);
            server.createContext("/internal/restaurants/1/quote", exchange -> {
                REQUEST_COUNT.incrementAndGet();
                LAST_REQUEST.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                LAST_METHOD.set(exchange.getRequestMethod());
                StubResponse response = RESPONSE.get();
                try {
                    Thread.sleep(response.delayMillis());
                    byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(response.status(), bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (IOException exception) {
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start test restaurant", exception);
        }
    }

    private record StubResponse(int status, String body, long delayMillis) {
    }
}
