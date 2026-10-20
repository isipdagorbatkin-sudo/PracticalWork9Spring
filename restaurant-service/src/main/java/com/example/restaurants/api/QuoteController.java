package com.example.restaurants.api;
import com.example.restaurants.api.RestaurantDtos.*;
import com.example.restaurants.restaurant.RestaurantService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
@Hidden
@RestController
@RequestMapping("/internal/restaurants")
public class QuoteController {
    private final RestaurantService service;
    public QuoteController(RestaurantService service) { this.service=service; }
    @PostMapping("/{id}/quote")
    public Quote quote(@PathVariable @Positive Long id, @Valid @RequestBody QuoteRequest request) {
        return service.quote(id, request);
    }
}
