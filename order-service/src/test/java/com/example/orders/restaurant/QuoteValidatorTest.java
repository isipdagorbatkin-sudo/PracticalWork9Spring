package com.example.orders.restaurant;

import com.example.orders.api.OrderItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteValidatorTest {
    private static final RestaurantQuote.Item PIZZA = item(1L, "Пицца", 2, "550.00", "1100.00");
    private static final List<OrderItemRequest> REQUEST = List.of(new OrderItemRequest(1L, 2));

    @Test
    void acceptsValidQuoteWithExactDecimalArithmetic() {
        RestaurantQuote quote = new RestaurantQuote(1L, "Пиццерия", List.of(PIZZA), new BigDecimal("1100.00"));
        assertThat(QuoteValidator.validate(1L, REQUEST, quote)).isSameAs(quote);
    }

    @ParameterizedTest
    @MethodSource("invalidQuotes")
    void rejectsQuoteThatDoesNotMatchOrder(RestaurantQuote quote) {
        assertThatThrownBy(() -> QuoteValidator.validate(1L, REQUEST, quote))
                .isInstanceOf(RestaurantUnavailableException.class);
    }

    static Stream<RestaurantQuote> invalidQuotes() {
        return Stream.of(
                null,
                new RestaurantQuote(2L, "Пиццерия", List.of(PIZZA), new BigDecimal("1100.00")),
                new RestaurantQuote(1L, " ", List.of(PIZZA), new BigDecimal("1100.00")),
                new RestaurantQuote(1L, "Пиццерия", null, new BigDecimal("1100.00")),
                new RestaurantQuote(1L, "Пиццерия", List.of(), new BigDecimal("1100.00")),
                new RestaurantQuote(1L, "Пиццерия", List.of(PIZZA, PIZZA), new BigDecimal("2200.00")),
                quote(item(99L, "Пицца", 2, "550.00", "1100.00")),
                quote(item(1L, "Пицца", 1, "1100.00", "1100.00")),
                quote(item(1L, "", 2, "550.00", "1100.00")),
                quote(item(1L, "Пицца", 2, "-550.00", "-1100.00")),
                quote(item(1L, "Пицца", 2, "550.001", "1100.002")),
                quote(item(1L, "Пицца", 2, "550.00", "1101.00")),
                new RestaurantQuote(1L, "Пиццерия", List.of(PIZZA), new BigDecimal("1000.00")),
                new RestaurantQuote(1L, "Пиццерия", List.of(PIZZA), null));
    }

    private static RestaurantQuote quote(RestaurantQuote.Item item) {
        return new RestaurantQuote(1L, "Пиццерия", List.of(item), new BigDecimal("1100.00"));
    }

    private static RestaurantQuote.Item item(Long id, String name, Integer quantity, String unitPrice, String lineTotal) {
        return new RestaurantQuote.Item(id, name, quantity, new BigDecimal(unitPrice), new BigDecimal(lineTotal));
    }
}
