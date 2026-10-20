package com.example.restaurants.restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByRestaurant_IdOrderByIdAsc(Long restaurantId);
    Optional<MenuItem> findByIdAndRestaurant_Id(Long id, Long restaurantId);
}
