package com.example.deliveries.api;
import com.example.deliveries.delivery.Delivery;
import com.example.deliveries.delivery.DeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
public final class DeliveryDtos {
    private DeliveryDtos() {}
    public record CreateDelivery(@NotNull @Positive @Schema(example="1") Long orderId,
            @NotBlank @Size(max=100) @Schema(example="Алексей") String courierName) {
        public CreateDelivery { courierName=courierName==null ? null : courierName.strip(); }
    }
    public record ChangeStatus(@NotNull @Schema(example="IN_TRANSIT") DeliveryStatus status) {}
    public record DeliveryView(Long id,Long orderId,String courierName,String deliveryAddress,
            DeliveryStatus status,Instant createdAt,Instant updatedAt) {
        public static DeliveryView from(Delivery d) {
            return new DeliveryView(d.getId(),d.getOrderId(),d.getCourierName(),d.getDeliveryAddress(),
                    d.getStatus(),d.getCreatedAt(),d.getUpdatedAt());
        }
    }
}
