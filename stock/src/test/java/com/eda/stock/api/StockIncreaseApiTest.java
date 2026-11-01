package com.eda.stock.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.common.outbox.OutboxRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.eda.stock.application.StockRegistrationService;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class StockIncreaseApiTest extends IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StockRegistrationService stockRegistrationService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private StringRedisTemplate redis;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        stockRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("재고를 늘리면 DB 와 Redis 가 같은 값으로 늘고, StockIncreased 를 남긴다")
    void increase() throws Exception {
        registerStock(1L);

        increase(1L, "{ \"quantity\": 100 }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(1))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.availableQuantity").value(100));

        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(100);
        assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("100");
        assertThat(outboxRepository.findAll())
                .filteredOn(message -> message.getMessageType().equals("StockIncreased"))
                .singleElement().satisfies(message -> {
                    assertThat(message.getTopic()).isEqualTo("stock.events");
                    assertThat(message.getMessageKey()).isEqualTo("1");
                    assertThat(message.getPayload())
                            .isEqualTo("{\"productId\":1,\"increasedQuantity\":100,\"quantity\":100}");
                });
    }

    @Test
    @DisplayName("재고 행이 없으면 404 STOCK_NOT_FOUND 다")
    void stockNotFound() throws Exception {
        increase(1L, "{ \"quantity\": 100 }")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{ \"quantity\": 0 }", "{ \"quantity\": 10001 }"})
    @DisplayName("quantity 가 누락되거나 1 ~ 10,000 을 벗어나면 400 VALIDATION_ERROR 다")
    void invalidQuantity(String body) throws Exception {
        registerStock(1L);

        increase(1L, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.quantity").isString());

        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isZero();
    }

    @Test
    @DisplayName("검증 메시지는 요청 언어와 상관없이 한국어다")
    void validationMessageInKorean() throws Exception {
        registerStock(1L);

        mockMvc.perform(post("/api/stocks/{productId}/increase", 1L).contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "en")
                        .content("{ \"quantity\": 10001 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("10000 이하여야 합니다"));
    }

    @Test
    @DisplayName("Redis 에 키가 없으면 DB 만 늘리고 availableQuantity 는 null 이다. 키를 새로 만들지 않는다")
    void availableUnknown() throws Exception {
        registerStock(1L);
        redis.delete("stock:available:1");

        increase(1L, "{ \"quantity\": 100 }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.availableQuantity").doesNotExist());

        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(100);
        assertThat(redis.hasKey("stock:available:1")).isFalse();
    }

    @Test
    @DisplayName("동시에 여러 번 늘려도 DB 와 Redis 가 모두 정확한 합계가 된다")
    void increaseConcurrently() throws Exception {
        registerStock(1L);
        int requests = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return increase(1L, "{ \"quantity\": 10 }").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
        }

        for (Future<Integer> status : statuses) {
            assertThat(status.get()).isEqualTo(200);
        }
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(100);
        assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("100");
    }

    private void registerStock(Long productId) {
        stockRegistrationService.registerIfAbsent(productId);
        stockRegistrationService.initializeAvailable(productId);
    }

    private ResultActions increase(Long productId, String body) throws Exception {
        return mockMvc.perform(post("/api/stocks/{productId}/increase", productId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
