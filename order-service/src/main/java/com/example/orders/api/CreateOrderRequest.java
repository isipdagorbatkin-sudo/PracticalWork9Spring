package com.example.orders.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Заказ из одного ресторана; цены рассчитывает сервер")
public record CreateOrderRequest(
        @NotNull @Positive @Schema(example = "1") Long restaurantId,
        @NotBlank @Size(max = 100) @Schema(example = "Артём") String customerName,
        @NotBlank @Size(max = 300) @Schema(example = "Москва, Ленина, 1") String deliveryAddress,
        @NotEmpty @Size(max = 20) List<@NotNull @Valid OrderItemRequest> items) {

    public CreateOrderRequest {
        customerName = customerName == null ? null : customerName.strip();
        deliveryAddress = deliveryAddress == null ? null : deliveryAddress.strip();
    }
}
