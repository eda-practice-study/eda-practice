package com.eda.stock.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.application.StockRegistrationService;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class StockIncreaseRollbackTest extends IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StockRegistrationService stockRegistrationService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoBean
    private OutboxWriter outboxWriter;

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Redis 를 늘린 뒤 트랜잭션이 롤백되면 Redis 도 되돌린다")
    void compensateRedisOnRollback() throws Exception {
        stockRegistrationService.registerIfAbsent(1L);
        stockRegistrationService.initializeAvailable(1L);
        given(outboxWriter.write(anyString(), anyString(), any())).willThrow(new IllegalStateException("outbox 실패"));

        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(post("/api/stocks/{productId}/increase", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"quantity\": 100 }"))
                .andExpect(status().isInternalServerError());

        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isZero();
        assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("0");
    }
}
