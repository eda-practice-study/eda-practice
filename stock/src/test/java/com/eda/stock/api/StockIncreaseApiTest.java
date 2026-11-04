package com.eda.stock.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eda.common.message.StockIncreased;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.stock.application.StockService;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 실제 HTTP와 H2 DB를 사용한다. Redis는 증가량 및 트랜잭션 경계 검증을 위한 대역이다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:stock-increase;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class StockIncreaseApiTest {
    private static final Long PRODUCT_ID = 987654L;

    @LocalServerPort
    private int port;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private StockService stockService;
    @Autowired
    private JsonMapper jsonMapper;
    @MockitoSpyBean
    private OutboxRepository outboxRepository;
    @MockitoBean
    private StockRedisRepository redisRepository;

    private final HttpClient client = HttpClient.newHttpClient();
    private AtomicLong available;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        stockRepository.deleteAll();
        Stock stock = Stock.createFor(PRODUCT_ID);
        stock.increase(50);
        stockRepository.saveAndFlush(stock);
        available = new AtomicLong(50);
        when(redisRepository.increase(eq(PRODUCT_ID), anyInt())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity())
                    .isGreaterThan(50);
            assertThat(outboxRepository.count()).isPositive();
            return available.addAndGet(invocation.getArgument(1, Integer.class));
        });
    }

    @Test
    void increaseExistingStockByRequestedAmount() throws Exception {
        var response = post(PRODUCT_ID, "{\"quantity\":100}");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = jsonMapper.readTree(response.body());
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("productId").asLong()).isEqualTo(PRODUCT_ID);
        assertThat(body.path("data").path("quantity").asInt()).isEqualTo(150);
        assertThat(body.path("data").path("availableQuantity").asLong()).isEqualTo(150);
        verify(redisRepository).increase(PRODUCT_ID, 100);
        assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isEqualTo(150);
        assertThat(outboxRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getTopic()).isEqualTo("stock.events");
            assertThat(event.getMessageKey()).isEqualTo(PRODUCT_ID.toString());
            assertThat(event.getMessageType()).isEqualTo("StockIncreased");
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(jsonMapper.readValue(event.getPayload(), StockIncreased.class))
                    .isEqualTo(new StockIncreased(PRODUCT_ID, 100, 150));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"quantity\":null}", "{\"quantity\":0}",
            "{\"quantity\":-1}", "{\"quantity\":10001}"})
    void rejectInvalidQuantity(String request) throws Exception {
        var response = post(PRODUCT_ID, request);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(jsonMapper.readTree(response.body()).path("errorCode").asString())
                .isEqualTo("VALIDATION_ERROR");
        assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isEqualTo(50);
        assertThat(outboxRepository.count()).isZero();
        verify(redisRepository, never()).increase(any(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10000})
    void acceptQuantityBoundaries(int quantity) throws Exception {
        var response = post(PRODUCT_ID, "{\"quantity\":" + quantity + "}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity())
                .isEqualTo(50 + quantity);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void missingStockReturns404() throws Exception {
        var response = post(PRODUCT_ID + 1, "{\"quantity\":100}");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(jsonMapper.readTree(response.body()).path("errorCode").asString())
                .isEqualTo("STOCK_NOT_FOUND");
        assertThat(outboxRepository.count()).isZero();
        verify(redisRepository, never()).increase(any(), anyInt());
    }

    @Test
    void redisFailureKeepsCommittedDatabaseAndReturnsNull() throws Exception {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isEqualTo(150);
            assertThat(outboxRepository.count()).isEqualTo(1);
            throw new RedisConnectionFailureException("테스트용 Redis 실패");
        }).when(redisRepository).increase(eq(PRODUCT_ID), anyInt());

        var response = post(PRODUCT_ID, "{\"quantity\":100}");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = jsonMapper.readTree(response.body()).path("data");
        assertThat(data.path("quantity").asInt()).isEqualTo(150);
        assertThat(data.has("availableQuantity")).isTrue();
        assertThat(data.path("availableQuantity").isNull()).isTrue();
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void outboxFailureRollsBackIncreaseAndDoesNotCallRedis() throws Exception {
        doThrow(new DataIntegrityViolationException("테스트용 Outbox 실패"))
                .when(outboxRepository).save(any(OutboxMessage.class));
        var response = post(PRODUCT_ID, "{\"quantity\":100}");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isEqualTo(50);
        assertThat(outboxRepository.count()).isZero();
        verify(redisRepository, never()).increase(any(), anyInt());
    }

    @Test
    void concurrentIncreasesPreserveBothAmountsAndEvents() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 초과");
                return stockService.increase(PRODUCT_ID, 10);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 초과");
                return stockService.increase(PRODUCT_ID, 20);
            });
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isEqualTo(80);
        var events = outboxRepository.findAll().stream()
                .map(event -> jsonMapper.readValue(event.getPayload(), StockIncreased.class)).toList();
        assertThat(events).extracting(StockIncreased::increasedQuantity).containsExactlyInAnyOrder(10, 20);
        assertThat(events).extracting(StockIncreased::quantity).contains(80);
    }

    private HttpResponse<String> post(Long productId, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/stocks/" + productId + "/increase"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
