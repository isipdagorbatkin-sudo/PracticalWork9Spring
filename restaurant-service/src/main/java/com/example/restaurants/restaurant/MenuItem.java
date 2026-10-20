package com.example.restaurants.restaurant;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
@Table(name = "menu_items")
public class MenuItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(nullable = false)
    private boolean available = true;
    protected MenuItem() {}
    public MenuItem(Restaurant restaurant, String name, BigDecimal price) {
        this.restaurant = restaurant; this.name = name; this.price = price;
    }
    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurant.getId(); }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
