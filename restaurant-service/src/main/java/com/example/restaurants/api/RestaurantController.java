package com.example.restaurants.api;
import com.example.restaurants.api.RestaurantDtos.*;
import com.example.restaurants.restaurant.RestaurantService;
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
@RequestMapping("/api/restaurants")
@Tag(name="Рестораны и меню", description="Рестораны, блюда и их доступность")
public class RestaurantController {
    private final RestaurantService service;
    public RestaurantController(RestaurantService service) { this.service=service; }
    @GetMapping @Operation(summary="Список ресторанов")
    public List<RestaurantView> list() { return service.list(); }
    @GetMapping("/{id}") @Operation(summary="Ресторан по номеру")
    public RestaurantView get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping @Operation(summary="Добавить ресторан")
    @ApiResponse(responseCode="201",description="Ресторан создан")
    public ResponseEntity<RestaurantView> create(@Valid @RequestBody CreateRestaurant request) {
        RestaurantView r=service.create(request);
        return ResponseEntity.created(URI.create("/api/restaurants/"+r.id())).body(r);
    }
    @PatchMapping("/{id}/availability") @Operation(summary="Открыть или закрыть ресторан")
    public RestaurantView availability(@PathVariable @Positive Long id, @Valid @RequestBody RestaurantAvailability request) {
        return service.availability(id, request.open());
    }
    @GetMapping("/{id}/menu") @Operation(summary="Посмотреть меню")
    public List<MenuItemView> menu(@PathVariable @Positive Long id) { return service.menu(id); }
    @GetMapping("/{id}/menu/{itemId}") @Operation(summary="Блюдо по номеру")
    public MenuItemView item(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId) {
        return service.getItem(id, itemId);
    }
    @PostMapping("/{id}/menu") @Operation(summary="Добавить блюдо в меню")
    @ApiResponse(responseCode="201",description="Блюдо добавлено")
    public ResponseEntity<MenuItemView> addItem(@PathVariable @Positive Long id, @Valid @RequestBody CreateMenuItem request) {
        MenuItemView item=service.addItem(id, request);
        return ResponseEntity.created(URI.create("/api/restaurants/"+id+"/menu/"+item.id())).body(item);
    }
    @PatchMapping("/{id}/menu/{itemId}/availability") @Operation(summary="Изменить доступность блюда")
    public MenuItemView availability(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId,
            @Valid @RequestBody MenuAvailability request) { return service.availability(id, itemId, request.available()); }
}
