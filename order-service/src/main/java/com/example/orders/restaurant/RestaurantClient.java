package com.example.orders.restaurant;

import com.example.orders.api.OrderItemRequest;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class RestaurantClient {
    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public RestaurantClient(RestClient restaurantRestClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = restaurantRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public RestaurantQuote quote(Long restaurantId, List<OrderItemRequest> items) {
        try {
            return circuitBreakerFactory.create("restaurantService")
                    .run(() -> requestQuote(restaurantId, items));
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof RestaurantBusinessException businessException) {
                    throw businessException;
                }
                if (cause instanceof RestaurantUnavailableException unavailableException) {
                    throw unavailableException;
                }
            }
            throw new RestaurantUnavailableException();
        }
    }

    private RestaurantQuote requestQuote(Long restaurantId, List<OrderItemRequest> items) {
        try {
            RestaurantQuote response = restClient.post()
                .uri("/internal/restaurants/{restaurantId}/quote", restaurantId)
                .body(new QuoteRequest(items))
                .retrieve()
                .body(RestaurantQuote.class);
            return QuoteValidator.validate(restaurantId, items, response);
        } catch (RestClientResponseException exception) {
            switch (exception.getStatusCode().value()) {
                case 400 -> throw new RestaurantBusinessException(HttpStatus.BAD_REQUEST, "INVALID_ORDER",
                        "Ресторан не смог обработать состав заказа");
                case 404 -> throw new RestaurantBusinessException(HttpStatus.NOT_FOUND, "RESTAURANT_OR_DISH_NOT_FOUND",
                        "Ресторан или блюдо не найдены");
                case 409 -> throw new RestaurantBusinessException(HttpStatus.CONFLICT, "RESTAURANT_NOT_AVAILABLE",
                        "Ресторан закрыт или выбранное блюдо недоступно");
                default -> throw new RestaurantUnavailableException();
            }
        } catch (RestClientException exception) {
            throw new RestaurantUnavailableException();
        }
    }

    private record QuoteRequest(List<OrderItemRequest> items) {
    }
}
