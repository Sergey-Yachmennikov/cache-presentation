package com.yachmennikov.cache_presentation.strategy.writebehind;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {
}
