package com.yachmennikov.cache_presentation;

import com.yachmennikov.cache_presentation.strategy.cacheaside.Product;
import com.yachmennikov.cache_presentation.strategy.cacheaside.ProductRepository;
import com.yachmennikov.cache_presentation.strategy.cacheaside.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Интеграционные тесты Cache-Aside стратегии кэширования.
 *
 * Цель тестов — проверить поведение кэша, а не бизнес-логику репозитория,
 * поэтому ProductRepository замокан: verify() позволяет точно считать
 * сколько раз реально вызывалась БД.
 *
 * Redis — реальный, в Testcontainers-контейнере (порт случайный, не 6379).
 * Это гарантирует, что кэш работает так же, как в продакшне.
 */
@DisplayName("Cache-Aside: ProductService")
class ProductServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ProductService productService;

    @MockitoBean
    ProductRepository productRepository;

    private static final Long PRODUCT_ID = 1L;

    private final Product product = Product.builder()
            .id(PRODUCT_ID)
            .name("MacBook Pro")
            .price(BigDecimal.valueOf(299_999.99))
            .build();

    @Test
    @DisplayName("Cache MISS: первый вызов идёт в БД и кладёт результат в кэш")
    void findById_onCacheMiss_loadsFromDbAndPopulatesCache() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        Product result = productService.findById(PRODUCT_ID);

        assertThat(result).isEqualTo(product);
        verify(productRepository, times(1)).findById(PRODUCT_ID);
    }

    @Test
    @DisplayName("Cache HIT: повторный вызов возвращает данные из кэша, БД не вызывается")
    void findById_onCacheHit_doesNotCallDatabase() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        productService.findById(PRODUCT_ID); // cache miss — идём в БД
        productService.findById(PRODUCT_ID); // cache hit — БД не вызывается
        productService.findById(PRODUCT_ID); // cache hit — БД не вызывается

        // При трёх вызовах БД должна быть вызвана только один раз
        verify(productRepository, times(1)).findById(PRODUCT_ID);
    }

    @Test
    @DisplayName("Cache EVICT после update: следующий вызов снова идёт в БД")
    void update_evictsCache_nextFindByIdGoesToDatabase() {
        Product updatedProduct = Product.builder()
                .id(PRODUCT_ID)
                .name("MacBook Pro M4")
                .price(BigDecimal.valueOf(349_999.99))
                .build();

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(updatedProduct);

        productService.findById(PRODUCT_ID);   // cache miss → кладём в кэш
        productService.findById(PRODUCT_ID);   // cache hit  → БД не вызывается

        productService.update(PRODUCT_ID, updatedProduct); // @CacheEvict — кэш очищен

        productService.findById(PRODUCT_ID);   // cache miss → идём в БД снова

        // findById должен быть вызван дважды: до update и после
        verify(productRepository, times(2)).findById(PRODUCT_ID);
    }

    @Test
    @DisplayName("Cache EVICT после delete: следующий вызов снова идёт в БД")
    void delete_evictsCache_nextFindByIdGoesToDatabase() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        productService.findById(PRODUCT_ID);   // cache miss → кладём в кэш
        productService.findById(PRODUCT_ID);   // cache hit  → БД не вызывается

        productService.delete(PRODUCT_ID);     // @CacheEvict — кэш очищен

        productService.findById(PRODUCT_ID);   // cache miss → идём в БД снова

        verify(productRepository, times(2)).findById(PRODUCT_ID);
        verify(productRepository, times(1)).deleteById(PRODUCT_ID);
    }

    @Test
    @DisplayName("Cache независим по ключу: разные id не мешают друг другу")
    void findById_differentIds_cachedIndependently() {
        Long otherId = 2L;
        Product otherProduct = Product.builder()
                .id(otherId)
                .name("iPhone 15 Pro")
                .price(BigDecimal.valueOf(129_999.99))
                .build();

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findById(otherId)).thenReturn(Optional.of(otherProduct));

        productService.findById(PRODUCT_ID);  // miss
        productService.findById(otherId);     // miss — отдельный ключ
        productService.findById(PRODUCT_ID);  // hit
        productService.findById(otherId);     // hit

        verify(productRepository, times(1)).findById(PRODUCT_ID);
        verify(productRepository, times(1)).findById(otherId);
    }

    @Test
    @DisplayName("save не затрагивает кэш: следующий GET делает cache miss и идёт в БД")
    void save_doesNotPopulateCache_nextFindByIdGoesToDatabase() {
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        productService.save(product);         // кэш не трогается
        productService.findById(PRODUCT_ID);  // cache miss → БД вызывается
        productService.findById(PRODUCT_ID);  // cache hit  → БД не вызывается

        // Если бы save() заполнил кэш, первый findById был бы HIT и verify(times(0)) упал бы.
        // Именно одно обращение к БД доказывает, что save() не трогал кэш.
        verify(productRepository, times(1)).findById(PRODUCT_ID);
    }
}
