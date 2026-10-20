package com.example.deliveries.api;
import com.example.deliveries.api.DeliveryDtos.*;
import com.example.deliveries.delivery.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
@RestController
@RequestMapping("/api/deliveries")
@Tag(name="Доставка",description="Назначение курьера и этапы доставки")
public class DeliveryController {
    private final DeliveryService service;
    public DeliveryController(DeliveryService service) { this.service=service; }
    @PostMapping @Operation(summary="Назначить курьера существующему заказу")
    @ApiResponse(responseCode="201",description="Доставка создана")
    public ResponseEntity<DeliveryView> create(@Valid @RequestBody CreateDelivery request) {
        DeliveryView d=service.create(request);
        return ResponseEntity.created(URI.create("/api/deliveries/"+d.id())).body(d);
    }
    @GetMapping @Operation(summary="Все доставки, сначала новые")
    public List<DeliveryView> list() { return service.list(); }
    @GetMapping("/{id}") @Operation(summary="Доставка по номеру")
    public DeliveryView get(@PathVariable @Positive Long id) { return service.get(id); }
    @GetMapping("/by-order/{orderId}") @Operation(summary="Найти доставку заказа")
    public DeliveryView byOrder(@PathVariable @Positive Long orderId) { return service.byOrder(orderId); }
    @PatchMapping("/{id}/status") @Operation(summary="Изменить этап доставки",description="CREATED → IN_TRANSIT → DELIVERED. Повтор текущего этапа допустим.")
    public DeliveryView changeStatus(@PathVariable @Positive Long id,@Valid @RequestBody ChangeStatus request) {
        return service.changeStatus(id,request.status());
    }
}
