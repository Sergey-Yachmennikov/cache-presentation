package com.yachmennikov.cache_presentation.strategy.readthrough;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
