package com.eda.stock.infra;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DirtiesContext
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:stock-cache-sync;DB_CLOSE_DELAY=-1")
class StockCacheSynchronizationIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("valkey/valkey:9.0-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired StockCache cache;
    @Autowired StringRedisTemplate template;

    @BeforeEach
    void clearKey() {
        template.delete("stock:available:1");
    }

    @Test
    @DisplayName("최신 버전이 먼저 도착하면 이전 버전은 null을 반환하고 최신 재고를 유지한다")
    void synchronize_shouldPreserveLatest_whenOlderArrivesLater() {
        // given
        cache.synchronize(1L, 130, 6L);

        // when
        Long available = cache.synchronize(1L, 110, 5L);

        // then
        assertThat(available).isNull();
        assertThat(template.opsForHash().entries("stock:available:1"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("quantity", "130", "version", "6"));
    }

    @Test
    @DisplayName("같은 버전을 다시 반영하면 수량을 더하지 않고 잘못된 수량을 보정한다")
    void synchronize_shouldCorrectQuantity_whenVersionEqual() {
        // given
        cache.synchronize(1L, 110, 5L);
        template.opsForHash().put("stock:available:1", "quantity", "999");

        // when
        cache.synchronize(1L, 110, 5L);
        Long repeated = cache.synchronize(1L, 110, 5L);

        // then
        assertThat(repeated).isEqualTo(110L);
        assertThat(template.opsForHash().get("stock:available:1", "quantity")).isEqualTo("110");
    }

    @Test
    @DisplayName("Lua 숫자의 정밀도 범위를 넘는 버전도 정확히 비교한다")
    void synchronize_shouldPreserveLatest_whenVersionExceedsDoublePrecision() {
        // given
        cache.synchronize(1L, 130, Long.MAX_VALUE);

        // when
        Long available = cache.synchronize(1L, 110, Long.MAX_VALUE - 1);

        // then
        assertThat(available).isNull();
        assertThat(template.opsForHash().get("stock:available:1", "version")).isEqualTo(Long.toString(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("기존 문자열 키는 DB 수량과 버전을 담은 Hash로 전환한다")
    void synchronize_shouldConvertHash_whenLegacyStringExists() {
        // given
        template.opsForValue().set("stock:available:1", "90");

        // when
        Long available = cache.synchronize(1L, 110, 5L);

        // then
        assertThat(available).isEqualTo(110L);
        assertThat(template.opsForHash().entries("stock:available:1"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("quantity", "110", "version", "5"));
    }

    @Test
    @DisplayName("키가 없으면 초기 수량과 버전을 0으로 저장하고 TTL을 설정하지 않는다")
    void initialize_shouldCreateHash_whenKeyMissing() {
        // when
        cache.initialize(1L);

        // then
        assertThat(template.opsForHash().entries("stock:available:1"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("quantity", "0", "version", "0"));
        assertThat(template.getExpire("stock:available:1")).isEqualTo(-1);
    }

    @Test
    @DisplayName("초기화가 늦게 도착해도 최신 수량과 버전을 덮어쓰지 않는다")
    void initialize_shouldPreserveLatest_whenInitializationDelayed() {
        // given
        cache.synchronize(1L, 110, 5L);

        // when
        cache.initialize(1L);

        // then
        assertThat(template.opsForHash().entries("stock:available:1"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("quantity", "110", "version", "5"));
    }

    @Test
    @DisplayName("기존 문자열 키도 초기화로 덮어쓰지 않는다")
    void initialize_shouldPreserveValue_whenLegacyStringExists() {
        // given
        template.opsForValue().set("stock:available:1", "25");

        // when
        cache.initialize(1L);

        // then
        assertThat(template.opsForValue().get("stock:available:1")).isEqualTo("25");
    }
}
