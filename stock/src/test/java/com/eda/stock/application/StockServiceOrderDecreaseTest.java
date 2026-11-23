package com.eda.stock.application;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.ErrorCode;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.domain.Stock;
import com.eda.stock.infrastructure.ProcessedOrderRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import com.eda.stock.infrastructure.StockRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 주문 생성 이벤트로 Stock DB 재고를 차감하는 부분 (Redis 는 Try 때 이미 예약했으므로 대상이 아님)
@SpringBootTest(properties = "outbox.enabled=false")
class StockServiceOrderDecreaseTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProcessedOrderRepository processedOrderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @MockitoBean
    private StockRedisRepository stockRedisRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        processedOrderRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("주문 수량만큼 DB 재고를 차감하고, 처리 기록과 재고 차감 이벤트를 함께 저장한다")
    void decrease() {
        // given
        saveStock(1L, 10);

        // when
        stockService.decreaseForOrder(100L, Map.of(1L, 3));

        // then
        assertThat(quantity(1L)).isEqualTo(7);
        assertThat(processedOrderRepository.existsByOrderId(100L)).isTrue();
        List<OutboxMessage> events = stockDecreasedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMessageKey()).isEqualTo("100");
        assertThat(events.get(0).getPayload())
                .isEqualTo("{\"orderId\":100,\"items\":[{\"productId\":1,\"decreasedQuantity\":3,\"quantity\":7}]}");
    }

    @Test
    @DisplayName("같은 주문 이벤트를 다시 받아도 DB 재고는 한 번만 차감한다")
    void duplicate() {
        // given
        saveStock(1L, 10);
        stockService.decreaseForOrder(100L, Map.of(1L, 3));

        // when
        stockService.decreaseForOrder(100L, Map.of(1L, 3));
        stockService.decreaseForOrder(100L, Map.of(1L, 3));

        // then
        assertThat(quantity(1L)).isEqualTo(7);
        assertThat(stockDecreasedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("여러 상품을 한 트랜잭션으로 차감한다")
    void multipleProducts() {
        // given
        saveStock(1L, 10);
        saveStock(2L, 5);
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        quantities.put(2L, 1);
        quantities.put(1L, 4);

        // when
        stockService.decreaseForOrder(100L, quantities);

        // then
        assertThat(quantity(1L)).isEqualTo(6);
        assertThat(quantity(2L)).isEqualTo(4);
    }

    @Test
    @DisplayName("DB 재고가 부족하면 아무것도 저장하지 않는다 (다시 받으면 재처리할 수 있게 처리 기록도 롤백)")
    void rollbackWhenOutOfStock() {
        // given
        saveStock(1L, 10);
        saveStock(2L, 1);
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        quantities.put(1L, 3);
        quantities.put(2L, 2);

        // when & then
        assertThatThrownBy(() -> stockService.decreaseForOrder(100L, quantities))
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
        assertThat(quantity(1L)).isEqualTo(10);
        assertThat(quantity(2L)).isEqualTo(1);
        assertThat(processedOrderRepository.existsByOrderId(100L)).isFalse();
        assertThat(stockDecreasedEvents()).isEmpty();
    }

    private void saveStock(Long productId, int quantity) {
        Stock stock = Stock.createFor(productId);
        stock.increase(quantity);
        stockRepository.save(stock);
    }

    private int quantity(Long productId) {
        return stockRepository.findByProductId(productId).orElseThrow().getQuantity();
    }

    private List<OutboxMessage> stockDecreasedEvents() {
        return outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType().equals("StockDecreased"))
                .toList();
    }
}
