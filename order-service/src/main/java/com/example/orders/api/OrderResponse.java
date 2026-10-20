package com.example.orders.api;

import com.example.orders.order.FoodOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long restaurantId,
        String restaurantName,
        String customerName,
        String deliveryAddress,
        @Schema(example = "CONFIRMED", allowableValues = "CONFIRMED") String status,
        List<Item> items,
        @Schema(description = "Сумма в рублях", example = "1100.00") BigDecimal totalAmount,
        Instant createdAt) {

    public static OrderResponse from(FoodOrder order) {
        return new OrderResponse(order.getId(), order.getRestaurantId(), order.getRestaurantName(),
                order.getCustomerName(), order.getDeliveryAddress(), order.getStatus(),
                order.getItems().stream().map(item -> new Item(item.getMenuItemId(), item.getName(),
                        item.getQuantity(), item.getUnitPrice(), item.getLineTotal())).toList(),
                order.getTotalAmount(), order.getCreatedAt());
    }

    public record Item(Long menuItemId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
