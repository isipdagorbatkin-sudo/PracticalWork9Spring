package com.example.orders.restaurant;

import com.example.orders.api.OrderItemRequest;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class QuoteValidator {
    private QuoteValidator() {
    }

    static RestaurantQuote validate(Long restaurantId, List<OrderItemRequest> requested, RestaurantQuote quote) {
        require(quote != null && restaurantId.equals(quote.restaurantId()));
        require(validName(quote.restaurantName()));
        require(quote.items() != null && quote.items().size() == requested.size());

        Map<Long, Integer> remaining = new HashMap<>();
        requested.forEach(item -> remaining.put(item.menuItemId(), item.quantity()));
        BigDecimal total = BigDecimal.ZERO;
        for (RestaurantQuote.Item item : quote.items()) {
            require(item != null);
            Integer quantity = remaining.remove(item.menuItemId());
            require(quantity != null && quantity.equals(item.quantity()));
            require(validName(item.name()) && validMoney(item.unitPrice()) && validMoney(item.lineTotal()));
            BigDecimal lineTotal = item.unitPrice().multiply(BigDecimal.valueOf(quantity));
            require(lineTotal.compareTo(item.lineTotal()) == 0);
            total = total.add(lineTotal);
        }
        require(remaining.isEmpty() && validMoney(quote.totalAmount()));
        require(total.compareTo(quote.totalAmount()) == 0);
        return quote;
    }

    private static boolean validName(String value) {
        return value != null && !value.isBlank() && value.length() <= 200;
    }

    private static boolean validMoney(BigDecimal value) {
        return value != null && value.signum() > 0 && value.scale() <= 2
                && value.precision() - value.scale() <= 17;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new RestaurantUnavailableException();
        }
    }
}
