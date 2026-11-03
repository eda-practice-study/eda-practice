package com.eda.product.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eda.product.domain.Product;
import com.eda.product.domain.ProductRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "outbox.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:product-api;DB_CLOSE_DELAY=-1"
})
class ProductApiTest {

    private static final String VALID_REQUEST = """
            {"name":"키보드","price":30000}
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("상품 생성 요청은 DB에 저장된 상품 정보와 201을 반환한다")
    void create() throws Exception {
        var result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("키보드"))
                .andExpect(jsonPath("$.data.price").value(30000))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        List<Product> products = productRepository.findAll();
        assertThat(products).hasSize(1);
        Product saved = products.getFirst();
        assertThat(saved.getName()).isEqualTo("키보드");
        assertThat(saved.getPrice()).isEqualByComparingTo("30000");
        assertThat(saved.getCreatedAt()).isNotNull();
        result.andExpect(jsonPath("$.data.productId").value(saved.getId()));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    @DisplayName("입력값이 규칙에 맞지 않으면 400을 반환하고 상품을 저장하지 않는다")
    void invalidRequest(String body) throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        assertThat(productRepository.count()).isZero();
    }

    private static Stream<String> invalidRequests() {
        return Stream.of(
                "{\"price\":30000}",
                "{\"name\":\"\",\"price\":30000}",
                "{\"name\":\"   \",\"price\":30000}",
                "{\"name\":\"" + "가".repeat(101) + "\",\"price\":30000}",
                "{\"name\":\"키보드\"}",
                "{\"name\":\"키보드\",\"price\":null}",
                "{\"name\":\"키보드\",\"price\":0}",
                "{\"name\":\"키보드\",\"price\":-1}",
                "{\"name\":\"키보드\",\"price\":1000001}",
                "{\"name\":\"키보드\",\"price\":100.5}"
        );
    }

    @ParameterizedTest
    @MethodSource("unreadableRequests")
    @DisplayName("읽을 수 없거나 비어 있는 JSON 요청은 400을 반환한다")
    void unreadableRequest(String body) throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        assertThat(productRepository.count()).isZero();
    }

    private static Stream<String> unreadableRequests() {
        return Stream.of("{\"name\":\"키보드\",\"price\":\"abc\"}", "{\"name\":", "");
    }

    @Test
    @DisplayName("같은 상품명으로 다시 생성하면 409를 반환하고 기존 상품만 남는다")
    void duplicateName() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NAME_DUPLICATED"));

        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이름의 동시 요청은 하나만 저장되고 나머지는 409를 반환한다")
    void concurrentDuplicateName() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var requests = Stream.generate(() -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST))
                        .andReturn().getResponse().getStatus();
            })).limit(2).toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    requests.get(0).get(10, TimeUnit.SECONDS),
                    requests.get(1).get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(201, 409);
            assertThat(productRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
