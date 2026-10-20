package com.example.restaurants.api;

import com.example.restaurants.restaurant.MenuItem;
import com.example.restaurants.restaurant.Restaurant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class RestaurantDtos {
    private RestaurantDtos() {}
    public record CreateRestaurant(
            @NotBlank @Size(max=100) @Schema(example="Пиццерия у дома") String name,
            @NotBlank @Size(max=300) @Schema(example="Москва, Пушкина, 10") String address) {
        public CreateRestaurant { name=trim(name); address=trim(address); }
    }
    public record RestaurantView(Long id, String name, String address, boolean open) {
        public static RestaurantView from(Restaurant r) { return new RestaurantView(r.getId(), r.getName(), r.getAddress(), r.isOpen()); }
    }
    public record CreateMenuItem(
            @NotBlank @Size(max=100) @Schema(example="Маргарита") String name,
            @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=10, fraction=2)
            @Schema(example="550.00") BigDecimal price) {
        public CreateMenuItem { name=trim(name); }
    }
    public record MenuItemView(Long id, Long restaurantId, String name, BigDecimal price, boolean available) {
        public static MenuItemView from(MenuItem item) {
            return new MenuItemView(item.getId(), item.getRestaurantId(), item.getName(), item.getPrice(), item.isAvailable());
        }
    }
    public record RestaurantAvailability(@NotNull Boolean open) {}
    public record MenuAvailability(@NotNull Boolean available) {}
    public record QuoteRequest(@NotEmpty @Size(max=20) List<@NotNull @Valid QuoteItem> items) {}
    public record QuoteItem(@NotNull @Positive Long menuItemId, @NotNull @Min(1) @Max(20) Integer quantity) {}
    public record QuotedItem(Long menuItemId, String name, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public record Quote(Long restaurantId, String restaurantName, List<QuotedItem> items, BigDecimal totalAmount) {}
    private static String trim(String text) { return text == null ? null : text.strip(); }
}
