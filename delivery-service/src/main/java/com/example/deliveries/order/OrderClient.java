package com.example.deliveries.order;
import com.example.deliveries.api.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
@Component
public class OrderClient {
    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    public OrderClient(RestClient orderRestClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.client=orderRestClient;
        this.circuitBreakerFactory=circuitBreakerFactory;
    }
    public OrderSnapshot get(Long id) {
        try {
            return circuitBreakerFactory.create("orderService").run(() -> request(id));
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ApiException apiException) {
                    throw apiException;
                }
            }
            throw unavailable();
        }
    }
    private OrderSnapshot request(Long id) {
        try {
            OrderSnapshot order=client.get().uri("/api/orders/{id}",id).retrieve().body(OrderSnapshot.class);
            if (order==null || !id.equals(order.id()) || !"CONFIRMED".equals(order.status())
                    || order.deliveryAddress()==null || order.deliveryAddress().isBlank() || order.deliveryAddress().length()>300) {
                throw unavailable();
            }
            return order;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value()==404) throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Заказ не найден");
            throw unavailable();
        } catch (RestClientException | IllegalStateException e) {
            throw unavailable();
        }
    }
    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ORDER_SERVICE_UNAVAILABLE","Сервис заказов недоступен или вернул некорректный ответ");
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record OrderSnapshot(Long id, String status, String deliveryAddress) {}
}
