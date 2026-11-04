package com.eda.stock.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// 별도 로컬 Redis를 실행하고 STOCK_TEST_REDIS_PORT=<포트>를 지정한다.
// DB와 Kafka 없이 실제 Repository의 Lua를 Redis에서 실행한다.
@EnabledIfEnvironmentVariable(named = "STOCK_TEST_REDIS_PORT", matches = "[0-9]+")
class StockRedisRepositoryTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static StockRedisRepository repository;

    private Long productId;
    private String key;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                "127.0.0.1", Integer.parseInt(System.getenv("STOCK_TEST_REDIS_PORT")));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        repository = new StockRedisRepository(redisTemplate);
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        productId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        key = "stock:available:" + productId;
    }

    @AfterEach
    void cleanUp() {
        // 테스트가 사용한 키만 삭제한다.
        redisTemplate.delete(key);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 0})
    @DisplayName("키가 있으면 0인 경우를 포함하여 요청 수량만큼 증가하고 새 값을 반환한다")
    void increaseExistingKey(int initialQuantity) {
        redisTemplate.opsForValue().set(key, String.valueOf(initialQuantity));

        Long result = repository.increase(productId, 100);

        assertThat(result).isEqualTo(initialQuantity + 100L);
        assertThat(redisTemplate.opsForValue().get(key))
                .isEqualTo(String.valueOf(initialQuantity + 100));
    }

    @Test
    @DisplayName("키가 없으면 null을 반환하며 반복 요청해도 새 키를 만들지 않는다")
    void doNotCreateMissingKey() {
        assertThat(redisTemplate.hasKey(key)).isFalse();

        assertThat(repository.increase(productId, 100)).isNull();
        assertThat(repository.increase(productId, 100)).isNull();

        assertThat(redisTemplate.hasKey(key)).isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).isNull();
    }
}
