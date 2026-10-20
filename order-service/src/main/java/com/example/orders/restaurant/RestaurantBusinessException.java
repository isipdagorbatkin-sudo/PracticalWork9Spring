package com.example.orders.restaurant;

import com.example.orders.api.ApiException;
import org.springframework.http.HttpStatus;

public class RestaurantBusinessException extends ApiException {
    public RestaurantBusinessException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
