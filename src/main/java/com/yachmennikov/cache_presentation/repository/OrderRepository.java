package com.yachmennikov.cache_presentation.repository;

import com.yachmennikov.cache_presentation.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
