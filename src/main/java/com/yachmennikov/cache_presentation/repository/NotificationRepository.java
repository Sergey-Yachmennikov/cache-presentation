package com.yachmennikov.cache_presentation.repository;

import com.yachmennikov.cache_presentation.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {
}
