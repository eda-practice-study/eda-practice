package com.eda.stock.application.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.in.AddStockUseCase.AddStockCommand;
import com.eda.stock.application.port.out.LoadStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StockCommandServiceTest {

    private FakeStockStore stockStore;
    private StockCommandService stockCommandService;

    @BeforeEach
    void setUp() {
        stockStore = new FakeStockStore();
        stockCommandService = new StockCommandService(stockStore, stockStore);
    }

    @Test
    @DisplayName("재고를 생성하면 수량 0으로 저장된다")
    void createStock() {
        // when
        stockCommandService.create(1L);

        // then
        assertThat(stockStore.findByProductId(1L))
                .isPresent()
                .get()
                .extracting(Stock::getQuantity)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("같은 상품 이벤트를 두 번 받아도 재고는 하나만 생성된다")
    void createStockIsIdempotent() {
        // when: Kafka 는 at-least-once 라 같은 이벤트가 중복 전달될 수 있다
        stockCommandService.create(1L);
        stockCommandService.create(1L);

        // then
        assertThat(stockStore.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 상품이면 각각 재고가 생성된다")
    void createStockForDifferentProducts() {
        // when
        stockCommandService.create(1L);
        stockCommandService.create(2L);

        // then
        assertThat(stockStore.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("재고를 등록하면 수량이 누적되고 최종 수량을 반환한다")
    void addStock() {
        // given
        stockCommandService.create(1L);

        // when
        int first = stockCommandService.add(new AddStockCommand(1L, 100));
        int second = stockCommandService.add(new AddStockCommand(1L, 30));

        // then
        assertThat(first).isEqualTo(100);
        assertThat(second).isEqualTo(130);
    }

    @Test
    @DisplayName("재고 row 가 없는 상품이면 UNKNOWN_PRODUCT 로 실패한다")
    void failToAddStockForUnknownProduct() {
        // when & then: 상품 미존재인지 이벤트 미전파인지 stock 은 구분할 수 없다
        assertThatThrownBy(() -> stockCommandService.add(new AddStockCommand(999L, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNKNOWN_PRODUCT);
    }

    @Test
    @DisplayName("수량이 0 이하이면 도메인 검증에 걸린다")
    void failToAddNonPositiveQuantity() {
        // given
        stockCommandService.create(1L);

        // when & then
        assertThatThrownBy(() -> stockCommandService.add(new AddStockCommand(1L, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_OPERATION);
    }

    /**
     * JPA 없이 저장소를 흉내내는 가짜 어댑터. productId 유일성까지 실제와 동일하게 맞춘다.
     */
    private static class FakeStockStore implements LoadStockPort, SaveStockPort {

        private final Map<Long, Stock> storeByProductId = new LinkedHashMap<>();
        private long sequence = 0L;

        @Override
        public boolean existsByProductId(Long productId) {
            return storeByProductId.containsKey(productId);
        }

        @Override
        public Optional<Stock> findByProductId(Long productId) {
            return Optional.ofNullable(storeByProductId.get(productId));
        }

        @Override
        public Stock save(Stock stock) {
            ReflectionTestUtils.setField(stock, "id", ++sequence);
            storeByProductId.put(stock.getProductId(), stock);
            return stock;
        }

        int count() {
            return storeByProductId.size();
        }
    }
}
