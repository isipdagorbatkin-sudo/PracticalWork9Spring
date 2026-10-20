package com.example.restaurants.restaurant;
import jakarta.persistence.*;
@Entity
@Table(name = "restaurants")
public class Restaurant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 300)
    private String address;
    @Column(nullable = false)
    private boolean open = true;
    protected Restaurant() {}
    public Restaurant(String name, String address) { this.name = name; this.address = address; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
}
