package com.eda.stock.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import com.eda.stock.domain.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(StockPersistenceAdapter.class)
class StockPersistenceAdapterTest {

    @Autowired
    private StockPersistenceAdapter stockPersistenceAdapter;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Test
    @DisplayName("재고를 저장하면 ID와 생성 시각이 채워진다")
    void saveStock() {
        // when
        Stock saved = stockPersistenceAdapter.save(Stock.createFor(1L));

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getQuantity()).isZero();
    }

    @Test
    @DisplayName("productId 로 재고 존재 여부를 확인한다")
    void existsByProductId() {
        // given
        stockPersistenceAdapter.save(Stock.createFor(1L));

        // when & then
        assertThat(stockPersistenceAdapter.existsByProductId(1L)).isTrue();
        assertThat(stockPersistenceAdapter.existsByProductId(999L)).isFalse();
    }

    @Test
    @DisplayName("같은 productId 로 재고를 두 번 저장하면 unique 제약에 걸린다")
    void rejectDuplicateProductId() {
        // given
        stockPersistenceAdapter.save(Stock.createFor(1L));

        // when & then: 멱등 체크가 뚫려도 DB 가 최후의 방어선이 된다
        assertThatThrownBy(() -> {
            stockPersistenceAdapter.save(Stock.createFor(1L));
            stockJpaRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
