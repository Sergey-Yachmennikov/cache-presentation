package com.yachmennikov.cache_presentation.pattern.memoization;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fibonacci")
@RequiredArgsConstructor
public class FibonacciController {

    private final FibonacciService service;

    /**
     * Сравнение наивной рекурсии и мемоизации.
     * Рекомендуется n от 35 до 45.
     *
     * GET /fibonacci/compare/40
     */
    @GetMapping("/compare/{n}")
    public FibonacciService.ComparisonResult compare(@PathVariable int n) {
        if (n > 50) {
            throw new IllegalArgumentException("n должно быть <= 50 (наивный алгоритм зависнет)");
        }
        return service.compare(n);
    }

    /**
     * Только мемоизированный вариант — без ограничения на n.
     *
     * GET /fibonacci/memoized/1000
     */
    @GetMapping("/memoized/{n}")
    public String memoized(@PathVariable long n) {
        return service.memoized(n).toString();
    }
}
