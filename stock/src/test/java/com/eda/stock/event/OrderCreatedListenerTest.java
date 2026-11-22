package com.eda.stock.event;

import static com.eda.stock.support.OrderEvents.orderCreated;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.eda.common.message.order.OrderCreated;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.application.StockIncreaseService;
import com.eda.stock.application.StockRegistrationService;
import com.eda.stock.domain.StockDecreaseRepository;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.support.IntegrationTest;
import com.eda.stock.support.OrderEvents;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class OrderCreatedListenerTest extends IntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private StockRegistrationService stockRegistrationService;

    @Autowired
    private StockIncreaseService stockIncreaseService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockDecreaseRepository stockDecreaseRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        stockDecreaseRepository.deleteAll();
        stockRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("주문 생성 이벤트를 받으면 DB 재고를 차감하고 StockDecreased 를 남긴다. Redis 는 다시 차감하지 않는다")
    void decrease() {
        stock(1L, 10);
        stock(2L, 5);

        kafkaTemplate.send(orderCreated(UUID.randomUUID(), order(1L, item(2L, 1), item(1L, 2))));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(stockDecreaseRepository.existsByOrderId(1L)).isTrue());
        assertThat(quantity(1L)).isEqualTo(8);
        assertThat(quantity(2L)).isEqualTo(4);
        assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("10");
        assertThat(stockDecreased()).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("stock.events");
            assertThat(message.getMessageKey()).isEqualTo("1");
            assertThat(message.getPayload()).isEqualTo("{\"orderId\":1,\"items\":["
                    + "{\"productId\":1,\"decreasedQuantity\":2,\"quantity\":8},"
                    + "{\"productId\":2,\"decreasedQuantity\":1,\"quantity\":4}]}");
        });
    }

    @Test
    @DisplayName("같은 주문 생성 이벤트를 다시 받아도 DB 재고는 한 번만 차감한다. messageId 가 달라도 같다")
    void redelivered() {
        stock(1L, 10);
        UUID messageId = UUID.randomUUID();
        OrderCreated order = order(1L, item(1L, 2));

        kafkaTemplate.send(orderCreated(messageId, order));
        kafkaTemplate.send(orderCreated(messageId, order));
        kafkaTemplate.send(orderCreated(UUID.randomUUID(), order));
        kafkaTemplate.send(orderCreated(UUID.randomUUID(), order(2L, item(1L, 1))));

        // 같은 파티션이라 2번 주문이 처리됐으면 앞의 재전송도 처리된 것이다
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(stockDecreaseRepository.existsByOrderId(2L)).isTrue());
        assertThat(quantity(1L)).isEqualTo(7);
        assertThat(stockDecreaseRepository.count()).isEqualTo(2);
        assertThat(stockDecreased()).extracting(OutboxMessage::getMessageKey).containsExactly("1", "2");
    }

    @Test
    @DisplayName("DB 재고가 부족하면 어떤 상품도 차감하지 않고 건너뛰며, 다음 주문은 처리한다")
    void notEnoughInDb() {
        stock(1L, 10);
        stock(2L, 0);

        kafkaTemplate.send(orderCreated(UUID.randomUUID(), order(1L, item(1L, 2), item(2L, 1))));
        kafkaTemplate.send(orderCreated(UUID.randomUUID(), order(2L, item(1L, 1))));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(stockDecreaseRepository.existsByOrderId(2L)).isTrue());
        assertThat(stockDecreaseRepository.existsByOrderId(1L)).isFalse();
        assertThat(quantity(1L)).isEqualTo(9);
        assertThat(quantity(2L)).isZero();
        assertThat(stockDecreased()).extracting(OutboxMessage::getMessageKey).containsExactly("2");
    }

    @Test
    @DisplayName("order.events 의 다른 이벤트는 무시한다")
    void ignoreOtherEvents() {
        stock(1L, 10);

        kafkaTemplate.send(OrderEvents.record(UUID.randomUUID(), "ProductSnapshotRegistered", "1",
                "{\"productId\":1,\"name\":\"키보드\",\"price\":30000}"));
        kafkaTemplate.send(orderCreated(UUID.randomUUID(), order(1L, item(1L, 1))));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(stockDecreaseRepository.existsByOrderId(1L)).isTrue());
        assertThat(quantity(1L)).isEqualTo(9);
    }

    private void stock(Long productId, int quantity) {
        stockRegistrationService.registerIfAbsent(productId);
        stockRegistrationService.initializeAvailable(productId);
        if (quantity > 0) {
            stockIncreaseService.increase(productId, quantity);
        }
    }

    private int quantity(Long productId) {
        return stockRepository.findByProductId(productId).orElseThrow().getQuantity();
    }

    private List<OutboxMessage> stockDecreased() {
        return outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType().equals("StockDecreased"))
                .toList();
    }

    private OrderCreated order(Long orderId, OrderCreated.Item... items) {
        return new OrderCreated(orderId, UUID.randomUUID().toString(), List.of(items));
    }

    private OrderCreated.Item item(Long productId, int quantity) {
        return new OrderCreated.Item(productId, quantity);
    }
}
