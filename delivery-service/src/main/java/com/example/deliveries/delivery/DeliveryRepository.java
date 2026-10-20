package com.example.deliveries.delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface DeliveryRepository extends JpaRepository<Delivery,Long> {
    boolean existsByOrderId(Long orderId);
    Optional<Delivery> findByOrderId(Long orderId);
    List<Delivery> findAllByOrderByIdDesc();
}
