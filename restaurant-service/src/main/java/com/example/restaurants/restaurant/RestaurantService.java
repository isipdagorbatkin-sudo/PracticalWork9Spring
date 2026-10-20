package com.example.restaurants.restaurant;

import com.example.restaurants.api.ApiException;
import com.example.restaurants.api.RestaurantDtos.*;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RestaurantService {
    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);
    private final RestaurantRepository restaurants;
    private final MenuItemRepository menu;
    public RestaurantService(RestaurantRepository restaurants, MenuItemRepository menu) {
        this.restaurants = restaurants; this.menu = menu;
    }
    public List<RestaurantView> list() { return restaurants.findAllByOrderByIdAsc().stream().map(RestaurantView::from).toList(); }
    public RestaurantView get(Long id) { return RestaurantView.from(requireRestaurant(id)); }
    @Transactional
    public RestaurantView create(CreateRestaurant request) {
        Restaurant r = restaurants.save(new Restaurant(request.name(), request.address()));
        log.info("Restaurant created: restaurantId={}", r.getId());
        return RestaurantView.from(r);
    }
    @Transactional
    public RestaurantView availability(Long id, boolean open) {
        Restaurant r = requireRestaurant(id); r.setOpen(open);
        log.info("Restaurant availability changed: restaurantId={}, open={}", id, open);
        return RestaurantView.from(r);
    }
    public List<MenuItemView> menu(Long restaurantId) {
        requireRestaurant(restaurantId);
        return menu.findByRestaurant_IdOrderByIdAsc(restaurantId).stream().map(MenuItemView::from).toList();
    }
    public MenuItemView getItem(Long restaurantId, Long itemId) { return MenuItemView.from(requireItem(restaurantId, itemId)); }
    @Transactional
    public MenuItemView addItem(Long restaurantId, CreateMenuItem request) {
        MenuItem item = menu.save(new MenuItem(requireRestaurant(restaurantId), request.name(), request.price()));
        log.info("Menu item created: restaurantId={}, menuItemId={}", restaurantId, item.getId());
        return MenuItemView.from(item);
    }
    @Transactional
    public MenuItemView availability(Long restaurantId, Long itemId, boolean available) {
        requireRestaurant(restaurantId);
        MenuItem item = requireItem(restaurantId, itemId); item.setAvailable(available);
        log.info("Menu item availability changed: restaurantId={}, menuItemId={}, available={}",
                restaurantId, itemId, available);
        return MenuItemView.from(item);
    }
    public Quote quote(Long restaurantId, QuoteRequest request) {
        Restaurant restaurant = requireRestaurant(restaurantId);
        if (!restaurant.isOpen()) throw unavailable();
        var seen = new HashSet<Long>();
        var items = new ArrayList<QuotedItem>();
        BigDecimal total = new BigDecimal("0.00");
        for (QuoteItem requested : request.items()) {
            if (!seen.add(requested.menuItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER", "Блюда не должны повторяться");
            }
            MenuItem item = requireItem(restaurantId, requested.menuItemId());
            if (!item.isAvailable()) throw unavailable();
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(requested.quantity()));
            items.add(new QuotedItem(item.getId(), item.getName(), requested.quantity(), item.getPrice(), lineTotal));
            total = total.add(lineTotal);
        }
        return new Quote(restaurantId, restaurant.getName(), items, total);
    }
    private Restaurant requireRestaurant(Long id) { return restaurants.findById(id).orElseThrow(RestaurantService::notFound); }
    private MenuItem requireItem(Long restaurantId, Long id) { return menu.findByIdAndRestaurant_Id(id, restaurantId).orElseThrow(RestaurantService::notFound); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_OR_DISH_NOT_FOUND", "Ресторан или блюдо не найдены"); }
    private static ApiException unavailable() { return new ApiException(HttpStatus.CONFLICT, "RESTAURANT_NOT_AVAILABLE", "Ресторан закрыт или блюдо недоступно"); }
}
