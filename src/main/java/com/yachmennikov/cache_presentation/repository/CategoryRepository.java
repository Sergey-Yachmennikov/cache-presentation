package com.yachmennikov.cache_presentation.repository;

import com.yachmennikov.cache_presentation.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
