package com.eda.stock.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.RequestHistoryRepository;
import com.eda.stock.domain.RequestHistoryId;
import com.eda.stock.domain.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DirtiesContext
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=stock",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class StockIncreaseApiIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("valkey/valkey:9.0-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.connect-timeout", () -> "500ms");
        registry.add("spring.data.redis.timeout", () -> "500ms");
    }

    @Autowired WebApplicationContext context;
    @Autowired StockRepository stockRepository;
    @Autowired RequestHistoryRepository requestRepository;
    @Autowired OutboxRepository outboxRepository;
    @MockitoSpyBean StringRedisTemplate template;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        requestRepository.deleteAll();
        stockRepository.deleteAll();
        stockRepository.saveAndFlush(Stock.createFor(1L));
        template.opsForValue().set("stock:available:1", "0");
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 200})
    @DisplayName("같은 키를 재전송하면 증가량에 관계없이 409를 응답하고 재고를 추가하지 않는다")
    void increase_shouldReturnConflict_whenHttpKeyRepeated(int repeatedQuantity) throws Exception {
        // given
        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.availableQuantity").value(100));

        // when, then
        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + repeatedQuantity + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_STOCK_INCREASE_REQUEST"));

        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(100);
        assertThat(template.opsForValue().get("stock:available:1")).isEqualTo("100");
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(requestRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis 호출에 실패해도 DB와 Outbox를 유지하고 같은 키의 재전송은 거절한다")
    void increase_shouldPreserveDatabase_whenRedisUnavailable() throws Exception {
        doThrow(new RedisConnectionFailureException("unavailable")).when(template).opsForValue();

        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.availableQuantity").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_STOCK_INCREASE_REQUEST"));

        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(100);
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(requestRepository.findById(new RequestHistoryId("STOCK_INCREASE", "key"))).isPresent();
    }

    @Test
    @DisplayName("Redis 증가 후 응답을 받지 못해도 HTTP 재전송으로 증가분을 다시 더하지 않는다")
    void increase_shouldApplyOnce_whenRedisResponseLost() throws Exception {
        ValueOperations<String, String> values = spy(template.opsForValue());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new QueryTimeoutException("response lost");
        }).when(values).increment("stock:available:1", 100L);
        doReturn(values).when(template).opsForValue();

        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableQuantity").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_STOCK_INCREASE_REQUEST"));

        assertThat(values.get("stock:available:1")).isEqualTo("100");
        verify(values, times(1)).increment("stock:available:1", 100L);
    }

    @Test
    @DisplayName("동시에 같은 키를 받으면 하나만 성공하고 다른 요청은 409를 응답한다")
    void increase_shouldReturnOneConflict_whenSameKeyConcurrent() throws Exception {
        // given
        var barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // when
            Future<Integer> first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                        .andReturn().getResponse().getStatus();
            });

            // then
            List<Integer> statuses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(100);
        assertThat(template.opsForValue().get("stock:available:1")).isEqualTo("100");
        assertThat(requestRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }
}
