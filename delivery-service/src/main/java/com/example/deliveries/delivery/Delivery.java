package com.example.deliveries.delivery;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="deliveries", uniqueConstraints=@UniqueConstraint(name="uk_delivery_order",columnNames="order_id"))
public class Delivery {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    @Column(name="order_id",nullable=false)
    private Long orderId;
    @Column(nullable=false,length=100)
    private String courierName;
    @Column(nullable=false,length=300)
    private String deliveryAddress;
    @Enumerated(EnumType.STRING) @Column(nullable=false)
    private DeliveryStatus status=DeliveryStatus.CREATED;
    @Column(nullable=false)
    private Instant createdAt;
    @Column(nullable=false)
    private Instant updatedAt;
    protected Delivery() {}
    public Delivery(Long orderId,String courierName,String deliveryAddress) {
        this.orderId=orderId; this.courierName=courierName; this.deliveryAddress=deliveryAddress;
        this.createdAt=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); this.updatedAt=createdAt;
    }
    public void changeStatus(DeliveryStatus status) { this.status=status; this.updatedAt=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getCourierName() { return courierName; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public DeliveryStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
