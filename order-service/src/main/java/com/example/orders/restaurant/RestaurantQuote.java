package com.example.orders.restaurant;

import java.math.BigDecimal;
import java.util.List;

public record RestaurantQuote(Long restaurantId, String restaurantName, List<Item> items, BigDecimal totalAmount) {
    public record Item(Long menuItemId, String name, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
