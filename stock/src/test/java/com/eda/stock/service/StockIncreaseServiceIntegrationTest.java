package com.eda.stock.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.api.StockIncreaseResponse;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.RequestHistory;
import com.eda.stock.domain.RequestHistoryRepository;
import com.eda.stock.domain.RequestHistoryId;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infra.StockCache;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@DirtiesContext
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=stock",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class StockIncreaseServiceIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired StockIncreaseService service;
    @Autowired StockWriteService stockWriteService;
    @Autowired StockRepository stockRepository;
    @Autowired RequestHistoryRepository requestRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @MockitoBean StockCache stockCache;
    @MockitoSpyBean OutboxWriter outboxWriter;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        requestRepository.deleteAll();
        stockRepository.deleteAll();
        stockRepository.saveAndFlush(Stock.createFor(1L));
    }

    @Test
    @DisplayName("재고와 멱등성 이력 및 Outbox를 커밋한 뒤 Redis를 호출한다")
    void increaseForRequest_shouldCommitBeforeRedis_whenRequestNew() {
        when(stockCache.synchronize(1L, 10, 1L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(invocation.<Long>getArgument(2)).isEqualTo(1L);
            assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
            assertThat(requestRepository.findById(new RequestHistoryId("STOCK_INCREASE", "key"))).isPresent();
            assertThat(outboxRepository.count()).isEqualTo(1);
            return 10L;
        });

        StockIncreaseResponse response = service.increaseForRequest("key", 1L, 10);

        assertThat(response).isEqualTo(new StockIncreaseResponse(1L, 10, 10L));
        Stock stock = stockRepository.findByProductId(1L).orElseThrow();
        assertThat(stock.getVersion()).isEqualTo(1L);
        RequestHistory history = requestRepository.findById(new RequestHistoryId("STOCK_INCREASE", "key")).orElseThrow();
        assertThat(history.getId()).isEqualTo(new RequestHistoryId("STOCK_INCREASE", "key"));
        assertThat(history.isNew()).isFalse();
        assertThat(history.getCreatedAt()).isNotNull();
        List<OutboxMessage> messages = outboxRepository.findAll();
        assertThat(messages).hasSize(1);
        OutboxMessage event = messages.getFirst();
        assertThat(event.getTopic()).isEqualTo("stock.events");
        assertThat(event.getMessageKey()).isEqualTo("1");
        assertThat(event.getMessageType()).isEqualTo("StockIncreased");
        assertThat(mapper.readValue(event.getPayload(), StockIncreased.class))
                .isEqualTo(new StockIncreased(1L, 10, 10));
    }

    @Test
    @DisplayName("다른 요청 종류에서 사용한 키로 재고 증가를 요청하면 정상 처리한다")
    void increaseForRequest_shouldSucceed_whenOtherRequestTypeExists() {
        // given
        requestRepository.save(new RequestHistory("OTHER_REQUEST", "key"));
        when(stockCache.synchronize(1L, 10, 1L)).thenReturn(10L);

        // when
        StockIncreaseResponse response = service.increaseForRequest("key", 1L, 10);

        // then
        assertThat(response).isEqualTo(new StockIncreaseResponse(1L, 10, 10L));
        assertThat(requestRepository.count()).isEqualTo(2);
        assertThat(requestRepository.existsById(new RequestHistoryId("OTHER_REQUEST", "key"))).isTrue();
        assertThat(requestRepository.existsById(new RequestHistoryId("STOCK_INCREASE", "key"))).isTrue();
        assertThat(outboxRepository.count()).isEqualTo(1);
        verify(stockCache, times(1)).synchronize(1L, 10, 1L);
    }

    @Test
    @DisplayName("기존 증가 이력을 복사하면 생성 시각을 보존하고 같은 키의 증가를 거절한다")
    void increaseForRequest_shouldThrowConstraintException_whenHistoryMigrated() {
        // given
        jdbc.execute("""
                create table stock.stock_increase_request (
                    id bigint generated by default as identity primary key,
                    idempotency_key varchar(128) not null unique,
                    product_id bigint not null,
                    created_at timestamp with time zone not null
                )
                """);
        try {
            jdbc.update("""
                    insert into stock.stock_increase_request (idempotency_key, product_id, created_at)
                    values ('key', 1, '2026-11-01T00:00:00Z')
                    """);

            // when
            int copied = jdbc.update("""
                    insert into stock.request_history (request_type, idempotency_key, created_at)
                    select 'STOCK_INCREASE', idempotency_key, created_at
                    from stock.stock_increase_request
                    on conflict (request_type, idempotency_key) do nothing
                    """);

            // then
            assertThat(copied).isEqualTo(1);
            RequestHistory history = requestRepository.findById(new RequestHistoryId("STOCK_INCREASE", "key"))
                    .orElseThrow();
            assertThat(history.getCreatedAt()).isEqualTo(java.time.Instant.parse("2026-11-01T00:00:00Z"));
            assertThatThrownBy(() -> service.increaseForRequest("key", 1L, 10))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isZero();
            assertThat(outboxRepository.count()).isZero();
            verifyNoInteractions(stockCache);
        } finally {
            jdbc.execute("drop table stock.stock_increase_request");
        }
    }

    @Test
    @DisplayName("Outbox 저장에 실패하면 재고와 멱등성 이력도 롤백하고 Redis를 호출하지 않는다")
    void increaseForRequest_shouldRollback_whenOutboxSaveFails() {
        jdbc.execute("alter table stock.outbox_message add constraint reject_increase check (message_type <> 'StockIncreased')");
        try {
            assertThatThrownBy(() -> service.increaseForRequest("key", 1L, 10))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isZero();
            assertThat(requestRepository.count()).isZero();
            assertThat(outboxRepository.count()).isZero();
            verifyNoInteractions(stockCache);
        } finally {
            jdbc.execute("alter table stock.outbox_message drop constraint reject_increase");
        }
    }

    @Test
    @DisplayName("재고가 없으면 예외를 던지고 멱등성 이력을 롤백한다")
    void increaseForRequest_shouldRollbackHistory_whenStockMissing() {
        assertThatThrownBy(() -> service.increaseForRequest("key", 2L, 10))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND));
        assertThat(requestRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
        verifyNoInteractions(stockCache);
    }

    @Test
    @DisplayName("이미 처리한 키는 유일 제약 예외를 던지고 재고와 이벤트 및 Redis를 추가하지 않는다")
    void increaseForRequest_shouldThrowConstraintException_whenRequestRepeated() {
        // given
        when(stockCache.synchronize(1L, 10, 1L)).thenReturn(10L);
        service.increaseForRequest("key", 1L, 10);

        // when, then
        assertThatThrownBy(() -> service.increaseForRequest("key", 1L, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(requestRepository.count()).isEqualTo(1);
        verify(stockCache, times(1)).synchronize(1L, 10, 1L);
    }

    @Test
    @DisplayName("DB만 커밋된 키도 중복으로 거절하고 Redis를 호출하지 않는다")
    void increaseForRequest_shouldThrowConstraintException_whenOnlyDatabaseCommitted() {
        // given
        stockWriteService.increase("key", 1L, 10);

        // when, then
        assertThatThrownBy(() -> service.increaseForRequest("key", 1L, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(outboxRepository.count()).isEqualTo(1);
        verifyNoInteractions(stockCache);
    }

    @Test
    @DisplayName("같은 키의 요청을 동시에 보내면 재고와 이벤트를 한 번만 증가시킨다")
    void increaseForRequest_shouldApplyOnce_whenSameKeyConcurrent() throws Exception {
        var redisStarted = new CountDownLatch(1);
        var releaseRedis = new CountDownLatch(1);
        when(stockCache.synchronize(1L, 10, 1L)).thenAnswer(invocation -> {
            redisStarted.countDown();
            assertThat(releaseRedis.await(10, TimeUnit.SECONDS)).isTrue();
            return 10L;
        });
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<StockIncreaseResponse> first = executor.submit(() -> service.increaseForRequest("key", 1L, 10));
            try {
                assertThat(redisStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.increaseForRequest("key", 1L, 10))
                        .isInstanceOf(DataIntegrityViolationException.class);
            } finally {
                releaseRedis.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(new StockIncreaseResponse(1L, 10, 10L));
        }
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(outboxRepository.count()).isEqualTo(1);
        verify(stockCache, times(1)).synchronize(1L, 10, 1L);
    }

    @Test
    @DisplayName("같은 버전의 재고를 동시에 증가시키면 한 요청은 롤백하고 재시도하지 않는다")
    void increaseForRequest_shouldRollbackConflict_whenSameVersionConcurrent() throws Exception {
        var barrier = new CyclicBarrier(2);
        OutboxWriter writer = AopTestUtils.getUltimateTargetObject(outboxWriter);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            barrier.await(10, TimeUnit.SECONDS);
            return result;
        }).when(writer).write(eq("stock.events"), eq("1"), any(StockIncreased.class));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> {
                try { return service.increaseForRequest("first", 1L, 10); }
                catch (OptimisticLockingFailureException ex) { return ex; }
            });
            Future<Object> second = executor.submit(() -> {
                try { return service.increaseForRequest("second", 1L, 10); }
                catch (OptimisticLockingFailureException ex) { return ex; }
            });
            List<Object> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(StockIncreaseResponse.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(OptimisticLockingFailureException.class::isInstance)).hasSize(1);
        }
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(requestRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        verify(stockCache, times(1)).synchronize(1L, 10, 1L);
    }
}
