package com.example.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/gateway-test.yml",
        "eureka.client.enabled=false",
        "spring.cloud.loadbalancer.cache.enabled=false"
})
class GatewayRoutingTest {
    private static final DisposableServer DOWNSTREAM = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle((request, response) -> response
                    .header("Content-Type", "text/plain")
                    .sendString(Mono.just(request.uri())))
            .bindNow();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void useActualRoutesAndLocalDiscovery(DynamicPropertyRegistry registry) throws IOException {
        Path config = Path.of("../config-repo/api-gateway.yml");
        if (!Files.isRegularFile(config)) {
            config = Path.of("config-repo/api-gateway.yml");
        }
        var sources = new YamlPropertySourceLoader().load("gateway-routes", new FileSystemResource(config));
        for (var source : sources) {
            var properties = (EnumerablePropertySource<?>) source;
            for (String name : properties.getPropertyNames()) {
                if (!name.equals("server.port")) {
                    registry.add(name, () -> properties.getProperty(name));
                }
            }
        }
        for (String service : new String[]{"restaurant-service", "order-service", "delivery-service"}) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
                    () -> "http://127.0.0.1:" + DOWNSTREAM.port());
        }
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.disposeNow();
    }

    @Test
    void forwardsPublicPathsToDiscoveredServices() throws Exception {
        for (String path : new String[]{"/api/restaurants", "/api/restaurants/1/menu", "/api/orders/42", "/api/deliveries/7"}) {
            var response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path).isEqualTo(path);
        }
    }

    @Test
    void servesFrontendFromGatewayRoot() throws Exception {
        var response = get("/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(value ->
                assertThat(value).startsWith("text/html"));
        assertThat(response.body()).contains("Еда рядом", "/app.js", "/styles.css");
    }

    @Test
    void rewritesEachDocumentationPath() throws Exception {
        for (String name : new String[]{"restaurants", "orders", "deliveries"}) {
            var response = get("/docs/" + name);
            assertThat(response.statusCode()).as(name).isEqualTo(200);
            assertThat(response.body()).isEqualTo("/v3/api-docs");
        }
    }

    @Test
    void doesNotExposeInternalEndpointsOrAutomaticDiscoveryRoutes() throws Exception {
        for (String path : new String[]{"/internal/restaurants/1/quote", "/internal/orders/42", "/order-service/internal/orders/42"}) {
            assertThat(get(path).statusCode()).as(path).isEqualTo(404);
        }
    }

    @Test
    void allowsSwaggerPreflightAndRejectsOtherOrigins() throws Exception {
        var accepted = preflight("http://localhost:8090");
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:8090");
        assertThat(preflight("https://example.invalid").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        return client.send(HttpRequest.newBuilder(uri("/api/orders"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
