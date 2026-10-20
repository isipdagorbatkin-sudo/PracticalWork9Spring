package com.example.orders.order;

import com.example.orders.api.CreateOrderRequest;
import com.example.orders.restaurant.RestaurantQuote;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "food_orders")
public class FoodOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 200)
    private String restaurantName;

    @Column(nullable = false, length = 100)
    private String customerName;

    @Column(nullable = false, length = 300)
    private String deliveryAddress;

    @Column(nullable = false, length = 20)
    private String status;

    @ElementCollection
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "item_position")
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private Instant createdAt;

    protected FoodOrder() {
    }

    public FoodOrder(CreateOrderRequest request, RestaurantQuote quote) {
        restaurantId = quote.restaurantId();
        restaurantName = quote.restaurantName();
        customerName = request.customerName();
        deliveryAddress = request.deliveryAddress();
        status = "CONFIRMED";
        items = quote.items().stream().map(item -> new OrderItem(item.menuItemId(), item.name(),
                item.quantity(), item.unitPrice(), item.lineTotal()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        totalAmount = quote.totalAmount().setScale(2);
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public String getRestaurantName() { return restaurantName; }
    public String getCustomerName() { return customerName; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getStatus() { return status; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
}
