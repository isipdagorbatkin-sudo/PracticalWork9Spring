package com.example.orders.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<FoodOrder, Long> {
    List<FoodOrder> findAllByOrderByIdDesc();
}
