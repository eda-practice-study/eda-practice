package com.eda.stock.infrastructure;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 재고 예약 TCC 의 Lua 스크립트를 실제 Redis(Valkey) 에서 검증한다
@Testcontainers
class StockRedisRepositoryTest {

    private static final Duration RECORD_TTL = Duration.ofDays(1);
    private static final long P1 = 1L;
    private static final long P2 = 2L;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("valkey/valkey:9.0-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static StockRedisRepository repository;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new StockRedisRepository(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void flush() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("Try (reserve)")
    class Reserve {

        @Test
        @DisplayName("재고가 충분하면 available 에서 reserved 로 옮기고 예약을 기록한다")
        void reserve() {
            // given
            repository.initialize(P1, 10);
            UUID id = UUID.randomUUID();

            // when
            long result = repository.reserve(id, Map.of(P1, 3), 1_000L);

            // then
            assertThat(result).isZero();
            assertThat(stock(P1)).isEqualTo(new RedisStock(7, 3));
            assertThat(reservation(id)).containsEntry("status", "RESERVED").containsEntry("1", "3");
            assertThat(repository.findReservedBefore(1_000L, 10)).containsExactly(id.toString());
        }

        @Test
        @DisplayName("재고가 부족하면 부족한 상품의 순번을 돌려주고 아무것도 바꾸지 않는다")
        void outOfStock() {
            // given
            repository.initialize(P1, 2);
            UUID id = UUID.randomUUID();

            // when
            long result = repository.reserve(id, Map.of(P1, 3), 1_000L);

            // then
            assertThat(result).isEqualTo(1);
            assertThat(stock(P1)).isEqualTo(new RedisStock(2, 0));
            assertThat(reservation(id)).isEmpty();
            assertThat(repository.findReservedBefore(Long.MAX_VALUE, 10)).isEmpty();
        }

        @Test
        @DisplayName("재고 키가 없으면 재고 부족으로 본다")
        void missingKey() {
            assertThat(repository.reserve(UUID.randomUUID(), Map.of(P1, 1), 1_000L)).isEqualTo(1);
            assertThat(repository.find(P1)).isEmpty();
        }

        @Test
        @DisplayName("여러 상품 중 하나라도 부족하면 어느 상품도 차감하지 않는다")
        void allOrNothing() {
            // given
            repository.initialize(P1, 10);
            repository.initialize(P2, 1);
            Map<Long, Integer> quantities = new LinkedHashMap<>();
            quantities.put(P1, 3);
            quantities.put(P2, 2);

            // when
            long result = repository.reserve(UUID.randomUUID(), quantities, 1_000L);

            // then
            assertThat(result).isEqualTo(2);
            assertThat(stock(P1)).isEqualTo(new RedisStock(10, 0));
            assertThat(stock(P2)).isEqualTo(new RedisStock(1, 0));
        }

        @Test
        @DisplayName("같은 reservationId 로 여러 번 Try 해도 한 번만 차감한다")
        void idempotent() {
            // given
            repository.initialize(P1, 10);
            UUID id = UUID.randomUUID();

            // when
            long first = repository.reserve(id, Map.of(P1, 3), 1_000L);
            long second = repository.reserve(id, Map.of(P1, 3), 2_000L);
            long third = repository.reserve(id, Map.of(P1, 3), 3_000L);

            // then
            assertThat(List.of(first, second, third)).containsOnly(0L);
            assertThat(stock(P1)).isEqualTo(new RedisStock(7, 3));
        }

        @Test
        @DisplayName("재고 10개에 1개씩 동시에 100건 예약하면 10건만 성공한다")
        void concurrent() throws Exception {
            // given
            repository.initialize(P1, 10);
            int requests = 100;
            ExecutorService executor = Executors.newFixedThreadPool(32);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Long>> results = new ArrayList<>();

            // when
            for (int i = 0; i < requests; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return repository.reserve(UUID.randomUUID(), Map.of(P1, 1), 1_000L);
                }));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<Long> result : results) {
                if (result.get() == 0) {
                    succeeded++;
                }
            }
            executor.shutdown();

            // then
            assertThat(succeeded).isEqualTo(10);
            assertThat(stock(P1)).isEqualTo(new RedisStock(0, 10));
            assertThat(repository.findReservedBefore(Long.MAX_VALUE, requests)).hasSize(10);
        }
    }

    @Nested
    @DisplayName("Confirm")
    class Confirm {

        @Test
        @DisplayName("reserved 에서 예약 수량만큼 빼고 기록을 CONFIRMED 로 남긴다")
        void confirm() {
            // given
            repository.initialize(P1, 10);
            UUID id = UUID.randomUUID();
            repository.reserve(id, Map.of(P1, 3), 1_000L);

            // when
            long result = repository.confirm(id, RECORD_TTL);

            // then
            assertThat(result).isEqualTo(1);
            assertThat(stock(P1)).isEqualTo(new RedisStock(7, 0));
            assertThat(reservation(id)).containsEntry("status", "CONFIRMED");
            assertThat(redisTemplate.getExpire("reservation:" + id)).isPositive();
            assertThat(repository.findReservedBefore(Long.MAX_VALUE, 10)).isEmpty();
        }

        @Test
        @DisplayName("두 번 확정해도 reserved 는 한 번만 줄어든다")
        void idempotent() {
            // given
            repository.initialize(P1, 10);
            UUID id = UUID.randomUUID();
            repository.reserve(id, Map.of(P1, 3), 1_000L);
            repository.confirm(id, RECORD_TTL);

            // when
            long result = repository.confirm(id, RECORD_TTL);

            // then
            assertThat(result).isZero();
            assertThat(stock(P1)).isEqualTo(new RedisStock(7, 0));
        }

        @Test
        @DisplayName("확정된 ID 로 늦게 도착한 Try 는 다시 차감하지 않는다")
        void lateTryAfterConfirm() {
            // given
            repository.initialize(P1, 10);
            UUID id = UUID.randomUUID();
            repository.reserve(id, Map.of(P1, 3), 1_000L);
            repository.confirm(id, RECORD_TTL);

            // when
            long result = repository.reserve(id, Map.of(P1, 3), 2_000L);

            // then
            assertThat(result).isZero();
            assertThat(stock(P1)).isEqualTo(new RedisStock(7, 0));
        }

        @Test
        @DisplayName("예약이 없거나 취소됐으면 -1 을 돌려주고 아무것도 바꾸지 않는다")
        void notReserved() {
            // given
            repository.initialize(P1, 10);
            UUID cancelled = UUID.randomUUID();
            repository.reserve(cancelled, Map.of(P1, 3), 1_000L);
            repository.cancel(cancelled, RECORD_TTL);

            // when & then
            assertThat(repository.confirm(UUID.randomUUID(), RECORD_TTL)).isEqualTo(-1);
            assertThat(repository.confirm(cancelled, RECORD_TTL)).isEqualTo(-1);
            assertThat(stock(P1)).isEqualTo(new RedisStock(10, 0));
        }
    }

    @Nested
    @DisplayName("Cancel")
    class Cancel {

        @Test
        @DisplayName("예약한 수량만 reserved 에서 available 로 되돌린다")
        void cancel() {
            // given
            repository.initialize(P1, 10);
            UUID mine = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            repository.reserve(mine, Map.of(P1, 3), 1_000L);
            repository.reserve(other, Map.of(P1, 2), 1_000L);

            // when
            long result = repository.cancel(mine, RECORD_TTL);

            // then — 다른 주문의 reserved(2) 는 그대로
            assertThat(result).isEqualTo(1);
            assertThat(stock(P1)).isEqualTo(new RedisStock(8, 2));
            assertThat(reservation(mine)).containsEntry("status", "CANCELLED");
            assertThat(repository.findReservedBefore(Long.MAX_VALUE, 10)).containsExactly(other.toString());
        }

        @Test
        @DisplayName("두 번 취소해도 한 번만 되돌린다")
        void idempotent() {
            // given
            repository.initialize(P1, 10);
            UUID id = UUID.randomUUID();
            repository.reserve(id, Map.of(P1, 3), 1_000L);
            repository.cancel(id, RECORD_TTL);

            // when
            long result = repository.cancel(id, RECORD_TTL);

            // then
            assertThat(result).isZero();
            assertThat(stock(P1)).isEqualTo(new RedisStock(10, 0));
        }

        @Test
        @DisplayName("빈 롤백 — Try 보다 먼저 오면 취소 표식만 남기고, 늦게 도착한 Try 를 거부한다")
        void emptyRollbackThenLateTry() {
            // given
            repository.initialize(P1, 10);
            UUID id = UUID.randomUUID();

            // when
            long cancelled = repository.cancel(id, RECORD_TTL);
            long lateTry = repository.reserve(id, Map.of(P1, 3), 2_000L);

            // then
            assertThat(cancelled).isZero();
            assertThat(reservation(id)).containsExactly(Map.entry("status", "CANCELLED"));
            assertThat(redisTemplate.getExpire("reservation:" + id)).isPositive();
            assertThat(lateTry).isEqualTo(-1);
            assertThat(stock(P1)).isEqualTo(new RedisStock(10, 0));
        }

        @Test
        @DisplayName("이미 확정된 예약은 취소하지 않는다")
        void confirmed() {
            // given
            repository.initialize(P1, 10);
            UUID id = UUID.randomUUID();
            repository.reserve(id, Map.of(P1, 3), 1_000L);
            repository.confirm(id, RECORD_TTL);

            // when
            long result = repository.cancel(id, RECORD_TTL);

            // then
            assertThat(result).isEqualTo(-1);
            assertThat(stock(P1)).isEqualTo(new RedisStock(7, 0));
        }
    }

    @Test
    @DisplayName("기준 시각 이전에 예약된 채 남은 예약만 찾는다")
    void findReservedBefore() {
        // given
        repository.initialize(P1, 10);
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        repository.reserve(old, Map.of(P1, 1), 1_000L);
        repository.reserve(recent, Map.of(P1, 1), 5_000L);

        // when & then
        assertThat(repository.findReservedBefore(3_000L, 10)).containsExactly(old.toString());
    }

    private RedisStock stock(long productId) {
        return repository.find(productId).orElseThrow();
    }

    private Map<Object, Object> reservation(UUID reservationId) {
        return redisTemplate.opsForHash().entries("reservation:" + reservationId);
    }
}
