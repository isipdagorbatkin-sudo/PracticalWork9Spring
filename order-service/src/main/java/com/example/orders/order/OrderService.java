package com.example.orders.order;

import com.example.orders.api.ApiException;
import com.example.orders.api.CreateOrderRequest;
import com.example.orders.api.OrderItemRequest;
import com.example.orders.api.OrderResponse;
import com.example.orders.restaurant.RestaurantClient;
import com.example.orders.restaurant.RestaurantQuote;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository repository;
    private final RestaurantClient restaurantClient;

    public OrderService(OrderRepository repository, RestaurantClient restaurantClient) {
        this.repository = repository;
        this.restaurantClient = restaurantClient;
    }

    public OrderResponse create(CreateOrderRequest request) {
        long uniqueItems = request.items().stream().map(OrderItemRequest::menuItemId).distinct().count();
        if (uniqueItems != request.items().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER",
                    "Одно блюдо нельзя указывать дважды: используйте поле quantity");
        }

        RestaurantQuote quote = restaurantClient.quote(request.restaurantId(), request.items());
        FoodOrder order = repository.save(new FoodOrder(request, quote));
        log.info("Order created: orderId={}, restaurantId={}, itemCount={}",
                order.getId(), request.restaurantId(), request.items().size());
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return repository.findAllByOrderByIdDesc().stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        FoodOrder order = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Заказ не найден"));
        return OrderResponse.from(order);
    }
}
