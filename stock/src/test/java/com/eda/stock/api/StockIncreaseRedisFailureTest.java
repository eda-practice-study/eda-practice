package com.eda.stock.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.application.StockRegistrationService;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.redis.AvailableStockStore;
import com.eda.stock.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class StockIncreaseRedisFailureTest extends IntegrationTest {

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

    @MockitoSpyBean
    private AvailableStockStore availableStockStore;

    @BeforeEach
    void setUp() {
        stockRegistrationService.registerIfAbsent(1L);
        stockRegistrationService.initializeAvailable(1L);
    }

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        stockRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Redis 반영이 실패해도 DB 와 StockIncreased 는 반영하고 availableQuantity 를 null 로 응답한다")
    void redisFails() throws Exception {
        doThrow(new RedisConnectionFailureException("Redis 장애"))
                .when(availableStockStore).increaseIfPresent(anyLong(), anyInt());

        increase100()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.availableQuantity").doesNotExist());

        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(100);
        assertThat(outboxRepository.findAll()).extracting(message -> message.getMessageType())
                .contains("StockIncreased");
    }

    private ResultActions increase100() throws Exception {
        return MockMvcBuilders.webAppContextSetup(context).build()
                .perform(post("/api/stocks/{productId}/increase", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"quantity\": 100 }"));
    }
}
