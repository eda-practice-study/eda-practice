package com.eda.stock.service;

import static org.mockito.Mockito.*;

import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infra.StockCache;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class StockReconciliationServiceTest {
    private final StockRepository repository = mock(StockRepository.class);
    private final StockCache cache = mock(StockCache.class);
    private final StockReconciliationService service = new StockReconciliationService(repository, cache, 2);

    @Test
    @DisplayName("상품 ID 순서로 배치를 나눠 전체 DB 재고를 Redis에 반영한다")
    void reconcile_shouldProcessAllBatches_whenStocksExist() {
        // given
        var page = PageRequest.of(0, 2);
        when(repository.findByProductIdGreaterThanOrderByProductIdAsc(0, page))
                .thenReturn(List.of(stock(1L, 10, 1L), stock(3L, 20, 2L)));
        when(repository.findByProductIdGreaterThanOrderByProductIdAsc(3, page))
                .thenReturn(List.of(stock(5L, 30, 3L)));
        when(repository.findByProductIdGreaterThanOrderByProductIdAsc(5, page)).thenReturn(List.of());

        // when
        service.reconcile();

        // then
        verify(cache).synchronize(1L, 10, 1L);
        verify(cache).synchronize(3L, 20, 2L);
        verify(cache).synchronize(5L, 30, 3L);
        verify(repository).findByProductIdGreaterThanOrderByProductIdAsc(5, page);
        verifyNoMoreInteractions(cache);
    }

    @Test
    @DisplayName("한 상품의 Redis 반영이 실패해도 다음 상품을 처리한다")
    void reconcile_shouldContinue_whenOneStockFails() {
        // given
        var page = PageRequest.of(0, 2);
        when(repository.findByProductIdGreaterThanOrderByProductIdAsc(0, page))
                .thenReturn(List.of(stock(1L, 10, 1L), stock(2L, 20, 2L)));
        when(repository.findByProductIdGreaterThanOrderByProductIdAsc(2, page)).thenReturn(List.of());
        when(cache.synchronize(1L, 10, 1L)).thenReturn(null);

        // when
        service.reconcile();

        // then
        verify(cache, times(1)).synchronize(1L, 10, 1L);
        verify(cache).synchronize(2L, 20, 2L);
    }

    private Stock stock(Long productId, int quantity, long version) {
        Stock stock = Stock.createFor(productId);
        stock.increase(quantity);
        ReflectionTestUtils.setField(stock, "version", version);
        return stock;
    }
}
