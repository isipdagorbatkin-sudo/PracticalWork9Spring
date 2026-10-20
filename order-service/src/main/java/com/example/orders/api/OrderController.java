package com.example.orders.api;

import com.example.orders.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Заказы", description = "Подтверждённые заказы и сохранённые цены блюд")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Оформить заказ", description = "Проверяет меню ресторана и сохраняет цены на момент покупки. "
            + "Если ресторан недоступен, заказ не создаётся.")
    @ApiResponse(responseCode = "201", description = "Заказ создан")
    @ApiResponse(responseCode = "400", description = "Некорректный запрос", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Ресторан или блюдо не найдены", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Ресторан закрыт или блюдо недоступно", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Сервис ресторанов недоступен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse order = orderService.create(request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }

    @GetMapping
    @Operation(summary = "Все заказы, сначала новые")
    public List<OrderResponse> findAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить заказ по номеру")
    @ApiResponse(responseCode = "200", description = "Заказ найден")
    @ApiResponse(responseCode = "404", description = "Заказ не найден", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OrderResponse findById(@PathVariable @Positive Long id) {
        return orderService.findById(id);
    }
}
