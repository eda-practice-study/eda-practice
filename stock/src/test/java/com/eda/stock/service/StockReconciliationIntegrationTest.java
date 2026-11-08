package com.eda.stock.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.api.StockIncreaseResponse;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.domain.RequestHistoryRepository;
import com.eda.stock.infra.StockCache;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DirtiesContext
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=stock",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "stock.reconciliation.batch-size=1"
})
class StockReconciliationIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("valkey/valkey:9.0-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired StockIncreaseService increaseService;
    @Autowired StockReconciliationService reconciliationService;
    @Autowired StockRepository stockRepository;
    @Autowired RequestHistoryRepository requestRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired StringRedisTemplate template;
    @MockitoSpyBean StockCache cache;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        requestRepository.deleteAll();
        stockRepository.deleteAll();
        stockRepository.save(Stock.createFor(1L));
        template.delete(java.util.List.of("stock:available:1", "stock:available:2"));
    }

    @Test
    @DisplayName("증가 요청의 Redis 반영에 실패하면 대사가 DB 수량과 버전으로 복구한다")
    void reconcile_shouldRestoreCache_whenIncreaseCacheWriteFails() {
        // given
        doReturn(null).when(cache).synchronize(1L, 10, 1L);
        StockIncreaseResponse response = increaseService.increaseForRequest("first", 1L, 10);
        assertThat(response.availableQuantity()).isNull();
        assertThat(template.hasKey("stock:available:1")).isFalse();
        doCallRealMethod().when(cache).synchronize(1L, 10, 1L);

        // when
        reconciliationService.reconcile();

        // then
        assertThat(template.opsForHash().entries("stock:available:1"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("quantity", "10", "version", "1"));
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(requestRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("대사가 읽은 뒤 증가가 먼저 반영돼도 오래된 대사 결과로 덮어쓰지 않는다")
    void reconcile_shouldPreserveIncrease_whenReconciliationWriteDelayed() throws Exception {
        // given
        increaseService.increaseForRequest("first", 1L, 10);
        var snapshotRead = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        // 버전 1의 대사 쓰기만 멈춰 그 사이 버전 2의 증가를 반영한다.
        doAnswer(invocation -> {
            snapshotRead.countDown();
            assertThat(releaseWrite.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(cache).synchronize(1L, 10, 1L);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> reconciliation = executor.submit(reconciliationService::reconcile);
            try {
                assertThat(snapshotRead.await(10, TimeUnit.SECONDS)).isTrue();
                // when
                increaseService.increaseForRequest("second", 1L, 10);
            } finally {
                releaseWrite.countDown();
            }
            reconciliation.get(10, TimeUnit.SECONDS);
        }

        // then
        assertThat(template.opsForHash().entries("stock:available:1"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("quantity", "20", "version", "2"));
    }

    @Test
    @DisplayName("증가 요청의 Redis 쓰기가 늦어져도 최신 대사 결과에 증가분을 다시 더하지 않는다")
    void increaseForRequest_shouldPreserveReconciliation_whenIncreaseWriteDelayed() throws Exception {
        // given
        var firstCommitted = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        // 첫 증가의 커밋 후 Redis 쓰기를 멈추고, 두 번째 증가는 DB에만 반영한다.
        doAnswer(invocation -> {
            firstCommitted.countDown();
            assertThat(releaseWrite.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(cache).synchronize(1L, 10, 1L);
        doReturn(null).when(cache).synchronize(1L, 20, 2L);

        StockIncreaseResponse firstResponse;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<StockIncreaseResponse> first = executor.submit(() ->
                    increaseService.increaseForRequest("first", 1L, 10));
            try {
                assertThat(firstCommitted.await(10, TimeUnit.SECONDS)).isTrue();
                increaseService.increaseForRequest("second", 1L, 10);
                doCallRealMethod().when(cache).synchronize(1L, 20, 2L);
                // when
                reconciliationService.reconcile();
            } finally {
                releaseWrite.countDown();
            }
            firstResponse = first.get(10, TimeUnit.SECONDS);
        }

        // then
        assertThat(firstResponse).isEqualTo(new StockIncreaseResponse(1L, 10, null));
        Stock latestStock = stockRepository.findByProductId(1L).orElseThrow();
        assertThat(latestStock.getQuantity()).isEqualTo(20);
        assertThat(latestStock.getVersion()).isEqualTo(2L);
        assertThat(template.opsForHash().entries("stock:available:1"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("quantity", "20", "version", "2"));
    }

    @Test
    @DisplayName("첫 상품의 Redis 반영이 실패해도 다음 배치의 상품을 복구한다")
    void reconcile_shouldProcessLaterBatches_whenFirstRedisWriteFails() {
        // given
        stockRepository.save(Stock.createFor(2L));
        doReturn(null).when(cache).synchronize(1L, 0, 0L);

        // when
        reconciliationService.reconcile();

        // then
        assertThat(template.hasKey("stock:available:1")).isFalse();
        assertThat(template.opsForHash().entries("stock:available:2"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("quantity", "0", "version", "0"));
    }
}
