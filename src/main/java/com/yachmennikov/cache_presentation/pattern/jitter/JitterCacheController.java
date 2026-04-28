package com.yachmennikov.cache_presentation.pattern.jitter;

import com.yachmennikov.cache_presentation.strategy.cacheaside.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jitter/products")
@RequiredArgsConstructor
public class JitterCacheController {

    private final JitterCacheService jitterCacheService;

    /**
     * GET /jitter/products/{id}
     *
     * При первом запросе: [DB] Loading product 5... → [CACHE SET] product 5 → TTL 57 sec
     * При втором запросе: [CACHE HIT] product 5      (БД не вызывается)
     *
     * Запустите несколько раз подряд с разными id — в логах TTL будет разным:
     *   product 1 → TTL 53 sec
     *   product 2 → TTL 68 sec
     *   product 3 → TTL 61 sec
     * Кеши протухнут в разное время → нет одновременного stampede на БД.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(jitterCacheService.findById(id));
    }

    // Ручной сброс кеша для демонстрации повторного cache miss
    @DeleteMapping("/{id}/cache")
    public ResponseEntity<Void> evict(@PathVariable Long id) {
        jitterCacheService.evict(id);
        return ResponseEntity.noContent().build();
    }
}
