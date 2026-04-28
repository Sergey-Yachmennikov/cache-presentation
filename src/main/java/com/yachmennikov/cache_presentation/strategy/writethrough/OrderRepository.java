package com.yachmennikov.cache_presentation.strategy.writethrough;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
