package com.yachmennikov.cache_presentation.controller;

import com.yachmennikov.cache_presentation.entity.Coupon;
import com.yachmennikov.cache_presentation.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * Negative Caching READ:
     * - 200 + объект  → купон найден (из Redis или БД).
     * - 404           → купона нет. При первом запросе — из БД.
     *                   При повторном — null взят из Redis (БД не вызывалась).
     *
     * Проверить в логах: "[DB] Loading coupon..." появится только один раз
     * для несуществующего кода, пока не истечёт TTL кеша.
     */
    @GetMapping("/{code}")
    public ResponseEntity<Coupon> getByCode(@PathVariable String code) {
        Coupon coupon = couponService.findByCode(code);
        return coupon != null
                ? ResponseEntity.ok(coupon)
                : ResponseEntity.notFound().build();
    }

    // Negative Caching CREATE: запись в БД + сброс закешированного null
    @PostMapping
    public ResponseEntity<Coupon> create(@RequestBody Coupon coupon) {
        return ResponseEntity.ok(couponService.save(coupon));
    }

    // Negative Caching DELETE: удаление из БД + сброс кеша (следующий GET закеширует null)
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        couponService.delete(code);
        return ResponseEntity.noContent().build();
    }
}
