package com.yachmennikov.cache_presentation.strategy.cacheaside;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {}
