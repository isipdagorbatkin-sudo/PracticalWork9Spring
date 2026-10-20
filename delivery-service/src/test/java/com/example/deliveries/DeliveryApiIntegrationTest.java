package com.example.deliveries;

import com.example.deliveries.api.ApiException;
import com.example.deliveries.api.DeliveryDtos.CreateDelivery;
import com.example.deliveries.delivery.*;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.location=classpath:/application-test.yml",
        "spring.datasource.url=jdbc:h2:mem:deliveries;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.application.name=delivery-service", "app.order.read-timeout=500ms"})
@AutoConfigureMockMvc
class DeliveryApiIntegrationTest {
    private record Reply(int code,String body,long delay) {}
    private static final String ORDER="{\"id\":42,\"status\":\"CONFIRMED\",\"deliveryAddress\":\"Москва, Ленина, 1\",\"totalAmount\":1250}";
    private static final String REQUEST="{\"orderId\":42,\"courierName\":\"  Алексей  \"}";
    private static final AtomicReference<Reply> REPLY=new AtomicReference<>();
    private static final AtomicInteger CALLS=new AtomicInteger();
    private static final AtomicReference<CountDownLatch> BARRIER=new AtomicReference<>();
    private static final ExecutorService HTTP_THREADS=Executors.newCachedThreadPool();
    private static final HttpServer ORDERS=startServer();
    @Autowired MockMvc mvc;
    @Autowired DeliveryRepository repository;
    @Autowired DeliveryService service;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri",()->"http://localhost:"+ORDERS.getAddress().getPort());
    }
    @BeforeEach void reset() {
        repository.deleteAll();
        REPLY.set(new Reply(200,ORDER,0));
        CALLS.set(0);
        BARRIER.set(null);
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }
    @AfterAll static void stopServer() { ORDERS.stop(0); HTTP_THREADS.shutdownNow(); }
    private long create() throws Exception {
        String json=mvc.perform(post("/api/deliveries").contentType("application/json").content(REQUEST))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.courierName").value("Алексей"))
                .andExpect(jsonPath("$.deliveryAddress").value("Москва, Ленина, 1"))
                .andExpect(jsonPath("$.status").value("CREATED")).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(json,"$.id").longValue();
    }
    @Test void createsReadsAndCompletesDeliveryWithIdempotentStatus() throws Exception {
        long id=create();
        mvc.perform(get("/api/deliveries/by-order/42")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/deliveries")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        String response=mvc.perform(patch("/api/deliveries/"+id+"/status").contentType("application/json").content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String updatedAt=JsonPath.read(response,"$.updatedAt");
        mvc.perform(patch("/api/deliveries/"+id+"/status").contentType("application/json").content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").value(updatedAt));
        mvc.perform(patch("/api/deliveries/"+id+"/status").contentType("application/json").content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        REPLY.set(new Reply(503,"{}",0));
        mvc.perform(get("/api/deliveries/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        assertThat(CALLS.get()).isEqualTo(1);
    }
    @Test void rejectsDuplicateDeliveryAndInvalidTransitions() throws Exception {
        long id=create();
        mvc.perform(post("/api/deliveries").contentType("application/json").content(REQUEST))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_ALREADY_EXISTS"));
        mvc.perform(patch("/api/deliveries/"+id+"/status").contentType("application/json").content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_DELIVERY_TRANSITION"));
        service.changeStatus(id,DeliveryStatus.IN_TRANSIT); service.changeStatus(id,DeliveryStatus.DELIVERED);
        mvc.perform(patch("/api/deliveries/"+id+"/status").contentType("application/json").content("{\"status\":\"CREATED\"}"))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"orderId\":0,\"courierName\":\"Алексей\"}",
            "{\"orderId\":42,\"courierName\":\"  \"}","{\"orderId\":42.5,\"courierName\":\"Алексей\"}","not json"})
    void rejectsInvalidInputBeforeHttp(String request) throws Exception {
        mvc.perform(post("/api/deliveries").contentType("application/json").content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DELIVERY"));
        assertThat(CALLS.get()).isZero(); assertThat(repository.count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"status\":null}","{\"status\":\"UNKNOWN\"}","{\"status\":1}"})
    void rejectsMalformedStatuses(String request) throws Exception {
        long id=create();
        mvc.perform(patch("/api/deliveries/"+id+"/status").contentType("application/json").content(request))
                .andExpect(status().isBadRequest());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.CREATED);
    }
    @Test void returns404ForMissingOrderAndDelivery() throws Exception {
        REPLY.set(new Reply(404,"{}",0));
        mvc.perform(post("/api/deliveries").contentType("application/json").content(REQUEST))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mvc.perform(get("/api/deliveries/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/deliveries/by-order/999999")).andExpect(status().isNotFound());
        assertThat(repository.count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"null","not json","{}",
            "{\"id\":41,\"status\":\"CONFIRMED\",\"deliveryAddress\":\"Москва\"}",
            "{\"id\":42,\"status\":\"CANCELLED\",\"deliveryAddress\":\"Москва\"}",
            "{\"id\":42,\"status\":\"CONFIRMED\",\"deliveryAddress\":\"  \"}"})
    void rejectsInvalidOrderResponseWithoutSaving(String body) throws Exception {
        REPLY.set(new Reply(200,body,0));
        mvc.perform(post("/api/deliveries").contentType("application/json").content(REQUEST))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ORDER_SERVICE_UNAVAILABLE"));
        assertThat(repository.count()).isZero();
    }
    @Test void handlesFailureAndTimeoutWithoutSaving() throws Exception {
        REPLY.set(new Reply(500,"{}",0));
        mvc.perform(post("/api/deliveries").contentType("application/json").content(REQUEST))
                .andExpect(status().isServiceUnavailable());
        REPLY.set(new Reply(200,ORDER,1500));
        long start=System.nanoTime();
        mvc.perform(post("/api/deliveries").contentType("application/json").content(REQUEST))
                .andExpect(status().isServiceUnavailable());
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(3));
        assertThat(repository.count()).isZero();
    }
    @Test void databasePreventsConcurrentDuplicateDeliveries() throws Exception {
        BARRIER.set(new CountDownLatch(2));
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Callable<String> attempt=()-> {
                try { service.create(new CreateDelivery(42L,"Курьер")); return "CREATED"; }
                catch (ApiException e) { return e.getCode(); }
            };
            Future<String> first=workers.submit(attempt), second=workers.submit(attempt);
            assertThat(java.util.List.of(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED","DELIVERY_ALREADY_EXISTS");
            assertThat(repository.count()).isEqualTo(1);
        } finally { workers.shutdownNow(); BARRIER.set(null); }
    }
    @Test void databaseRejectsStaleVersionInsteadOfOverwritingStatus() throws Exception {
        long id=create();
        Delivery first=repository.findById(id).orElseThrow();
        Delivery stale=repository.findById(id).orElseThrow();
        first.changeStatus(DeliveryStatus.IN_TRANSIT); repository.saveAndFlush(first);
        stale.changeStatus(DeliveryStatus.DELIVERED);
        assertThatThrownBy(()->repository.saveAndFlush(stale))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }
    @Test void exposesDocumentation() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"))
                .andExpect(jsonPath("$.paths['/api/deliveries'].post.responses['201']").exists());
    }
    private static HttpServer startServer() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("localhost",0),0);
            server.setExecutor(HTTP_THREADS);
            server.createContext("/api/orders/",exchange->{
                CALLS.incrementAndGet();
                Reply reply=REPLY.get();
                try {
                    CountDownLatch gate=BARRIER.get();
                    if (gate!=null) { gate.countDown(); if (!gate.await(3,TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent request did not arrive"); }
                    Thread.sleep(reply.delay());
                    byte[] body=reply.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","application/json");
                    exchange.sendResponseHeaders(reply.code(),body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
            server.start(); return server;
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
