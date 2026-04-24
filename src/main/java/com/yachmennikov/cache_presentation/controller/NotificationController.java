package com.yachmennikov.cache_presentation.controller;

import com.yachmennikov.cache_presentation.entity.Notification;
import com.yachmennikov.cache_presentation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // Write-Behind READ: HIT → из Redis, MISS → из PostgreSQL + запись в Redis
    @GetMapping("/{id}")
    public ResponseEntity<Notification> getById(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getAll() {
        return ResponseEntity.ok(notificationService.findAll());
    }

    // Write-Behind CREATE: запись только в Redis, в PostgreSQL — асинхронно через шедулер
    @PostMapping
    public ResponseEntity<Notification> create(@RequestBody Notification notification) {
        return ResponseEntity.ok(notificationService.create(notification));
    }

    // Write-Behind UPDATE: обновление только в Redis, в PostgreSQL — асинхронно через шедулер
    @PutMapping("/{id}")
    public ResponseEntity<Notification> update(@PathVariable String id, @RequestBody Notification notification) {
        return ResponseEntity.ok(notificationService.update(id, notification));
    }

    // Write-Behind DELETE: инвалидация Redis + удаление из PostgreSQL
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
