package com.example.orders.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull @Positive @Schema(example = "1") Long menuItemId,
        @NotNull @Min(1) @Max(20) @Schema(example = "2") Integer quantity) {
}
