package com.eda.stock.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

// Redis 동작(SETNX, Lua)은 mock 으로 검증할 수 없어 진짜 Valkey 를 띄운다. Docker 가 필요하다.
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = "product.events")
public abstract class IntegrationTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("valkey/valkey:9.0-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
