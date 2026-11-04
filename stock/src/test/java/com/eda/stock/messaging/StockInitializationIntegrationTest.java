package com.eda.stock.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eda.common.message.Message;
import com.eda.common.message.MessageHeader;
import com.eda.common.message.ProductCreated;
import com.eda.common.message.StockInitialized;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

// 별도 로컬 Redis를 실행한 뒤 STOCK_TEST_REDIS_PORT=<포트> ./gradlew :stock:test --rerun-tasks 로 실행한다.
// 실제 Kafka 수신 대신 Listener를 직접 호출하여 DB·Redis·Outbox 연결을 검증한다.
@EnabledIfEnvironmentVariable(named = "STOCK_TEST_REDIS_PORT", matches = "[0-9]+")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:stock-initialization;DB_CLOSE_DELAY=-1")
class StockInitializationIntegrationTest {

    @Autowired
    private ProductEventListener listener;

    @Autowired
    private StockRepository stockRepository;

    @MockitoSpyBean
    private OutboxRepository outboxRepository;

    @MockitoSpyBean
    private StockRedisRepository stockRedisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    private Long productId;
    private String redisKey;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> System.getenv("STOCK_TEST_REDIS_PORT"));
    }

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        stockRepository.deleteAll();
        productId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        redisKey = "stock:available:" + productId;
    }

    @AfterEach
    void cleanUpRedis() {
        // 다른 Redis 데이터는 건드리지 않고 이 테스트의 키만 정리한다.
        redisTemplate.delete(redisKey);
    }

    @Test
    @DisplayName("상품 생성 이벤트를 받으면 DB와 Redis에 재고 0을 저장하고 Outbox에 기록한다")
    void initializeStock() {
        listener.onProductCreated(productCreatedRecord());

        assertThat(stockRepository.findAll()).singleElement().satisfies(stock -> {
            assertThat(stock.getProductId()).isEqualTo(productId);
            assertThat(stock.getQuantity()).isZero();
        });
        assertThat(redisTemplate.opsForValue().get(redisKey)).isEqualTo("0");
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("stock.events");
            assertThat(message.getMessageKey()).isEqualTo(productId.toString());
            assertThat(message.getMessageType()).isEqualTo("StockInitialized");
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(jsonMapper.readValue(message.getPayload(), StockInitialized.class))
                    .isEqualTo(new StockInitialized(productId, 0));
        });
    }

    @Test
    @DisplayName("같은 이벤트를 다시 받아도 DB·Redis의 기존 수량과 등록 이벤트를 유지한다")
    void preserveQuantitiesOnRedelivery() {
        var record = productCreatedRecord();
        listener.onProductCreated(record);
        var originalMessageId = outboxRepository.findAll().getFirst().getMessageId();
        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        stock.increase(10);
        stockRepository.saveAndFlush(stock);
        redisTemplate.opsForValue().set(redisKey, "7");

        listener.onProductCreated(record);

        assertThat(stockRepository.findAll()).singleElement().satisfies(saved ->
                assertThat(saved.getQuantity()).isEqualTo(10));
        assertThat(redisTemplate.opsForValue().get(redisKey)).isEqualTo("7");
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message ->
                assertThat(message.getMessageId()).isEqualTo(originalMessageId));
    }

    @Test
    @DisplayName("Redis 키만 없을 때 재처리하면 기존 DB 수량으로 초기화하고 이벤트를 추가하지 않는다")
    void initializeMissingRedisKeyOnRedelivery() {
        var record = productCreatedRecord();
        listener.onProductCreated(record);
        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        stock.increase(10);
        stockRepository.saveAndFlush(stock);
        redisTemplate.delete(redisKey);

        listener.onProductCreated(record);

        assertThat(redisTemplate.opsForValue().get(redisKey)).isEqualTo("10");
        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis 실패 시 DB·Outbox는 커밋되어 있고 재처리하면 Redis 초기화를 다시 시도한다")
    void retryAfterRedisFailure() {
        var record = productCreatedRecord();
        var failure = new RedisConnectionFailureException("테스트용 Redis 연결 실패");
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(stockRepository.count()).isEqualTo(1);
            assertThat(outboxRepository.count()).isEqualTo(1);
            throw failure;
        }).doCallRealMethod().when(stockRedisRepository).initializeIfAbsent(productId, 0);

        assertThatThrownBy(() -> listener.onProductCreated(record)).isSameAs(failure);

        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(redisTemplate.hasKey(redisKey)).isFalse();
        var originalMessageId = outboxRepository.findAll().getFirst().getMessageId();

        // Kafka의 자동 재시도를 검증하는 것이 아니라 같은 메시지를 다시 전달한다.
        listener.onProductCreated(record);

        assertThat(redisTemplate.opsForValue().get(redisKey)).isEqualTo("0");
        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message ->
                assertThat(message.getMessageId()).isEqualTo(originalMessageId));
    }

    @Test
    @DisplayName("Outbox 저장이 실패하면 재고를 롤백하고 Redis 호출 전에 예외를 전달한다")
    void databaseFailureDoesNotInitializeRedis() {
        var failure = new DataIntegrityViolationException("테스트용 Outbox 저장 실패");
        doThrow(failure).when(outboxRepository).save(any(OutboxMessage.class));

        assertThatThrownBy(() -> listener.onProductCreated(productCreatedRecord())).isSameAs(failure);

        assertThat(stockRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
        assertThat(redisTemplate.hasKey(redisKey)).isFalse();
        verify(stockRedisRepository, never()).initializeIfAbsent(any(), anyInt());
    }

    private ConsumerRecord<String, String> productCreatedRecord() {
        Message<ProductCreated> message = Message.of(new ProductCreated(productId, "키보드", 30000L));
        var record = new ConsumerRecord<>(
                "product.events", 0, 0L, productId.toString(),
                jsonMapper.writeValueAsString(message.payload()));
        record.headers().add(MessageHeader.MESSAGE_ID,
                message.messageId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.MESSAGE_TYPE,
                message.messageType().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.OCCURRED_AT,
                message.occurredAt().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
