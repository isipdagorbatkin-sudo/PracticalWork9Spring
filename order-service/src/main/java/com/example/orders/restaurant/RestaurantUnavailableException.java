package com.example.orders.restaurant;

import com.example.orders.api.ApiException;
import org.springframework.http.HttpStatus;

public class RestaurantUnavailableException extends ApiException {
    public RestaurantUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "RESTAURANT_UNAVAILABLE",
                "Сервис ресторанов временно недоступен. Попробуйте оформить заказ позже");
    }
}
