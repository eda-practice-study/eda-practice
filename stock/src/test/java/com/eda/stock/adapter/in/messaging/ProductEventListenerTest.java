package com.eda.stock.adapter.in.messaging;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.application.port.in.CreateStockUseCase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductEventListenerTest {

    @Test
    @DisplayName("상품 생성 이벤트에서 productId 만 꺼내 재고 생성 유스케이스로 넘긴다")
    void delegateProductIdToUseCase() {
        // given
        RecordingCreateStockUseCase createStockUseCase = new RecordingCreateStockUseCase();
        ProductEventListener listener = new ProductEventListener(createStockUseCase);

        // when
        listener.onProductCreated(ProductCreatedEvent.of(1L, "티셔츠", BigDecimal.valueOf(10000)));

        // then
        assertThat(createStockUseCase.received).containsExactly(1L);
    }

    private static class RecordingCreateStockUseCase implements CreateStockUseCase {

        private final List<Long> received = new ArrayList<>();

        @Override
        public void create(Long productId) {
            received.add(productId);
        }
    }
}
