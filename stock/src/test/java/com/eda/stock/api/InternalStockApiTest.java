package com.eda.stock.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.stock.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

class InternalStockApiTest extends IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StringRedisTemplate redis;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("여러 상품을 한 번에 차감하고, 주문 키로 차감 내역을 TTL 과 함께 남긴다")
    void decrease() throws Exception {
        available(1L, 10);
        available(2L, 5);
        String orderKey = UUID.randomUUID().toString();

        decrease(orderKey, item(1L, 2), item(2L, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(available(1L)).isEqualTo("8");
        assertThat(available(2L)).isEqualTo("4");
        assertThat(redis.hasKey("stock:order:" + orderKey)).isTrue();
        assertThat(redis.getExpire("stock:order:" + orderKey)).isBetween(1L, 3600L);
    }

    @Test
    @DisplayName("한 상품이라도 부족하면 409 OUT_OF_STOCK 이고 어떤 상품도 차감하지 않는다")
    void outOfStock() throws Exception {
        available(1L, 10);
        available(2L, 0);
        String orderKey = UUID.randomUUID().toString();

        decrease(orderKey, item(1L, 2), item(2L, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));

        assertThat(available(1L)).isEqualTo("10");
        assertThat(available(2L)).isEqualTo("0");
        assertThat(redis.hasKey("stock:order:" + orderKey)).isFalse();
    }

    @Test
    @DisplayName("재고 키가 없으면 409 OUT_OF_STOCK 이고 키를 새로 만들지 않는다")
    void noAvailableKey() throws Exception {
        decrease(UUID.randomUUID().toString(), item(1L, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));

        assertThat(redis.hasKey("stock:available:1")).isFalse();
    }

    @Test
    @DisplayName("같은 주문 키로 다시 요청하면 한 번만 차감하고 성공으로 답한다")
    void decreaseIdempotent() throws Exception {
        available(1L, 10);
        String orderKey = UUID.randomUUID().toString();

        decrease(orderKey, item(1L, 2)).andExpect(status().isOk());
        decrease(orderKey, item(1L, 2)).andExpect(status().isOk());

        assertThat(available(1L)).isEqualTo("8");
    }

    @Test
    @DisplayName("restore 는 차감한 만큼 되돌리고, 다시 불러도 한 번만 되돌린다")
    void restore() throws Exception {
        available(1L, 10);
        available(2L, 5);
        String orderKey = UUID.randomUUID().toString();
        decrease(orderKey, item(1L, 2), item(2L, 1)).andExpect(status().isOk());

        restore(orderKey).andExpect(status().isOk());
        restore(orderKey).andExpect(status().isOk());

        assertThat(available(1L)).isEqualTo("10");
        assertThat(available(2L)).isEqualTo("5");
    }

    @Test
    @DisplayName("차감보다 restore 가 먼저 오면 취소 표시를 남겨, 늦게 온 차감을 거부한다")
    void restoreBeforeDecrease() throws Exception {
        available(1L, 10);
        String orderKey = UUID.randomUUID().toString();

        restore(orderKey).andExpect(status().isOk());
        decrease(orderKey, item(1L, 2)).andExpect(status().isConflict());

        assertThat(available(1L)).isEqualTo("10");
        assertThat(redis.getExpire("stock:order:" + orderKey)).isBetween(1L, 3600L);
    }

    @Test
    @DisplayName("restore 할 때 재고 키가 없으면 키를 새로 만들지 않는다")
    void restoreWithoutAvailableKey() throws Exception {
        available(1L, 10);
        String orderKey = UUID.randomUUID().toString();
        decrease(orderKey, item(1L, 2)).andExpect(status().isOk());
        redis.delete("stock:available:1");

        restore(orderKey).andExpect(status().isOk());

        assertThat(redis.hasKey("stock:available:1")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }",
            "{ \"orderKey\": \"%s\", \"items\": [] }",
            "{ \"orderKey\": \"%s\", \"items\": [{ \"productId\": 1, \"quantity\": 0 }] }",
            "{ \"orderKey\": \"%s\", \"items\": [{ \"quantity\": 1 }] }",
            "{ \"orderKey\": \"%s\", \"items\": [{ \"productId\": 1, \"quantity\": 1 }, { \"productId\": 1, \"quantity\": 1 }] }"
    })
    @DisplayName("주문 키 누락, 빈 items, 1 미만 수량, productId 누락, 같은 상품 중복은 400 VALIDATION_ERROR 다")
    void invalidRequest(String body) throws Exception {
        available(1L, 10);

        mockMvc.perform(post("/internal/stocks/decrease").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        assertThat(available(1L)).isEqualTo("10");
    }

    @Test
    @DisplayName("재고 10개에 1개씩 동시에 100건 차감하면 10건만 성공하고 Redis 재고는 0 이 된다")
    void decreaseConcurrently() throws Exception {
        available(1L, 10);
        int requests = 100;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return decrease(UUID.randomUUID().toString(), item(1L, 1))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
        }

        List<Integer> results = new ArrayList<>();
        for (Future<Integer> status : statuses) {
            results.add(status.get());
        }
        assertThat(results).filteredOn(status -> status == 200).hasSize(10);
        assertThat(results).filteredOn(status -> status == 409).hasSize(90);
        assertThat(available(1L)).isEqualTo("0");
        assertThat(redis.keys("stock:order:*")).hasSize(10);
    }

    private void available(Long productId, int quantity) {
        redis.opsForValue().set("stock:available:" + productId, String.valueOf(quantity));
    }

    private String available(Long productId) {
        return redis.opsForValue().get("stock:available:" + productId);
    }

    private String item(Long productId, int quantity) {
        return "{ \"productId\": %d, \"quantity\": %d }".formatted(productId, quantity);
    }

    private ResultActions decrease(String orderKey, String... items) throws Exception {
        return mockMvc.perform(post("/internal/stocks/decrease").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"orderKey\": \"%s\", \"items\": [%s] }".formatted(orderKey, String.join(",", items))));
    }

    private ResultActions restore(String orderKey) throws Exception {
        return mockMvc.perform(post("/internal/stocks/restore").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"orderKey\": \"%s\" }".formatted(orderKey)));
    }
}
