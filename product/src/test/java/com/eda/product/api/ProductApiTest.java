package com.eda.product.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.product.domain.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "outbox.enabled=false")
class ProductApiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("상품을 생성하면 201 과 상품 정보를 응답하고, 같은 트랜잭션에 ProductCreated 를 outbox 에 남긴다")
    void create() throws Exception {
        create("""
                { "name": "키보드", "price": 30000 }
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").isNumber())
                .andExpect(jsonPath("$.data.name").value("키보드"))
                .andExpect(jsonPath("$.data.price").value(30000))
                .andExpect(jsonPath("$.data.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));

        Long productId = productRepository.findAll().getFirst().getId();
        List<OutboxMessage> outbox = outboxRepository.findAll();
        assertThat(outbox).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("product.events");
            assertThat(message.getMessageKey()).isEqualTo(String.valueOf(productId));
            assertThat(message.getMessageType()).isEqualTo("ProductCreated");
            assertThat(message.getPayload())
                    .isEqualTo("{\"productId\":" + productId + ",\"name\":\"키보드\",\"price\":30000}");
        });
    }

    @Test
    @DisplayName("앞뒤 공백을 지운 이름이 이미 있으면 409 PRODUCT_NAME_DUPLICATED 를 응답하고 이벤트를 남기지 않는다")
    void duplicatedName() throws Exception {
        create("""
                { "name": "키보드", "price": 30000 }
                """).andExpect(status().isCreated());

        create("""
                { "name": "  키보드 ", "price": 10000 }
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NAME_DUPLICATED"));

        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            { "price": 30000 }                     | name
            { "name": "  ", "price": 30000 }       | name
            { "name": "키보드" }                    | price
            { "name": "키보드", "price": 0 }        | price
            { "name": "키보드", "price": 1000001 }  | price
            """)
    @DisplayName("name · price 가 누락되거나 범위를 벗어나면 400 VALIDATION_ERROR 와 필드별 메시지를 응답한다")
    void invalidRequest(String body, String field) throws Exception {
        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors." + field).isString());

        assertThat(productRepository.count()).isZero();
    }

    @Test
    @DisplayName("이름이 100자를 넘으면 400 이다")
    void tooLongName() throws Exception {
        create("{ \"name\": \"" + "가".repeat(101) + "\", \"price\": 30000 }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isString());
    }

    @Test
    @DisplayName("같은 이름으로 동시에 생성하면 하나만 201 이고 나머지는 409 다 (500 없음)")
    void duplicatedNameConcurrently() throws Exception {
        int requests = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return create("""
                            { "name": "모니터", "price": 250000 }
                            """).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
        }

        List<Integer> results = new ArrayList<>();
        for (Future<Integer> status : statuses) {
            results.add(status.get());
        }
        assertThat(results).containsOnly(201, 409);
        assertThat(results).filteredOn(code -> code == 201).hasSize(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static org.hamcrest.Matcher<String> matchesPattern(String regex) {
        return org.hamcrest.Matchers.matchesPattern(regex);
    }
}
