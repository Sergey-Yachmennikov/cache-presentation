package com.yachmennikov.cache_presentation.pattern.memoization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Мемоизация на примере чисел Фибоначчи.
 *
 * Мемоизация — частный случай кэширования:
 * результаты вычислений сохраняются в таблице (memo),
 * чтобы не пересчитывать их при повторных вызовах.
 *
 * Без мемоизации: fib(n) порождает дерево рекурсивных вызовов
 * с огромным количеством повторных вычислений.
 *
 *   fib(5)
 *   ├── fib(4)
 *   │   ├── fib(3)
 *   │   │   ├── fib(2) ← вычисляется заново
 *   │   │   └── fib(1)
 *   │   └── fib(2)    ← вычисляется заново
 *   └── fib(3)        ← вычисляется заново
 *       ├── fib(2)    ← вычисляется заново
 *       └── fib(1)
 *
 * Сложность: O(2^n) — экспоненциальная.
 *
 * С мемоизацией: каждое значение вычисляется ровно один раз,
 * остальные вызовы — это просто чтение из Map.
 *
 * Сложность: O(n) — линейная.
 */
@Slf4j
@Service
public class FibonacciService {

    /**
     * Таблица мемоизации: ключ → уже вычисленный результат.
     * ConcurrentHashMap обеспечивает потокобезопасность.
     */
    private final Map<Long, BigInteger> memo = new ConcurrentHashMap<>();

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    /**
     * Наивная рекурсия без мемоизации.
     * Используем int чтобы ограничить n <= 50 — иначе зависнет.
     */
    public BigInteger naive(int n) {
        if (n <= 1) return BigInteger.valueOf(n);
        return naive(n - 1).add(naive(n - 2));
    }

    /**
     * Рекурсия с мемоизацией.
     * При первом обращении к fib(n) — вычисляем и сохраняем в memo.
     * При повторном — берём готовый результат из memo (cache hit).
     */
    public BigInteger memoized(long n) {
        BigInteger cached = memo.get(n);
        if (cached != null) {
            hits.incrementAndGet();
            log.info("[MEMO HIT]  fib({}) → уже вычислено", n);
            return cached;
        }

        misses.incrementAndGet();
        log.info("[MEMO MISS] fib({}) → вычисляем", n);

        BigInteger result = n <= 1
                ? BigInteger.valueOf(n)
                : memoized(n - 1).add(memoized(n - 2));

        memo.put(n, result);
        return result;
    }

    /**
     * Сравнивает оба подхода и возвращает результат замера.
     * Рекомендуемый диапазон n: 35–45.
     * При n > 50 наивный метод будет выполняться очень долго.
     */
    public ComparisonResult compare(int n) {
        log.info("========================================");
        log.info("Сравнение fib({}) — без мемоизации vs с мемоизацией", n);

        // --- Без мемоизации ---
        log.info("--- Наивная рекурсия ---");
        long start = System.nanoTime();
        BigInteger naiveResult = naive(n);
        long naiveMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Результат: {}, Время: {} мс", naiveResult, naiveMs);

        // --- С мемоизацией (сброс состояния для чистого замера) ---
        memo.clear();
        hits.set(0);
        misses.set(0);

        log.info("--- Рекурсия с мемоизацией ---");
        start = System.nanoTime();
        BigInteger memoResult = memoized(n);
        long memoMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Результат: {}, Время: {} мс, Hits: {}, Misses: {}",
                memoResult, memoMs, hits.get(), misses.get());

        log.info("Ускорение: ~{}x", naiveMs == 0 ? "∞" : naiveMs / Math.max(memoMs, 1));
        log.info("========================================");

        return new ComparisonResult(n, naiveResult.toString(), naiveMs,
                memoResult.toString(), memoMs, hits.get(), misses.get());
    }

    public record ComparisonResult(
            int n,
            String naiveResult,
            long naiveMs,
            String memoResult,
            long memoMs,
            long cacheHits,
            long cacheMisses
    ) {}
}
