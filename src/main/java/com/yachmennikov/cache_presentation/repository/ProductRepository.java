package com.yachmennikov.cache_presentation.repository;

import com.yachmennikov.cache_presentation.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {}
