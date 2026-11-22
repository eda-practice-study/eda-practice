package com.eda.order.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRequestConflict;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.common.outbox.OutboxRepository;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderRepository;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.domain.ProductSnapshotRepository;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

// stock 은 MockRestServiceServer 로 대신한다. 타임아웃과 연결 실패의 예외 모양은 StockClientTest 가 실제 소켓으로 확인한 것과 같다.
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "stock.restore.retry.delay=10ms",
        "stock.restore.retry.max-delay=20ms",
        "stock.restore.retry.jitter=0ms"
})
@ExtendWith(OutputCaptureExtension.class)
class OrderApiTest {

    private static final String STOCK = "http://stock";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MockRestServiceServer stock;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @Autowired
    private ProductSnapshotRepository productSnapshotRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private MockMvc mockMvc;

    @TestConfiguration
    static class StockStub {

        private final RestClient.Builder builder = RestClient.builder().baseUrl(STOCK);
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean
        MockRestServiceServer stockServer() {
            return server;
        }

        @Bean
        @Primary
        RestClient testStockRestClient() {
            return builder.build();
        }

        // restore 를 요청 스레드에서 바로 실행해, 응답 뒤에 restore 요청이 있었는지 없었는지를 확정한다
        @Bean
        TaskExecutor restoreExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        productSnapshotRepository.save(ProductSnapshot.of(1L, "키보드", BigDecimal.valueOf(30000)));
        productSnapshotRepository.save(ProductSnapshot.of(2L, "마우스", BigDecimal.valueOf(15000)));
    }

    @AfterEach
    void tearDown() {
        stock.reset();
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
        productSnapshotRepository.deleteAll();
    }

    @Test
    @DisplayName("stock 에 차감을 요청한 뒤 주문과 OrderCreated 를 저장하고 201 로 응답한다")
    void create() throws Exception {
        AtomicReference<String> orderKey = new AtomicReference<>();
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/decrease"))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[1].productId").value(2))
                .andExpect(jsonPath("$.items[1].quantity").value(1))
                .andExpect(request -> orderKey.set(
                        request.getBody().toString().replaceAll(".*\"orderKey\":\"([^\"]+)\".*", "$1")))
                .andRespond(withSuccess());

        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 2 }, { \"productId\": 2, \"quantity\": 1 }] }")
                .andExpect(status().isCreated())
                .andExpect(json("$.success").value(true))
                .andExpect(json("$.data.orderId").isNumber())
                .andExpect(json("$.data.status").value("CREATED"))
                .andExpect(json("$.data.totalAmount").value(75000))
                .andExpect(json("$.data.items[0].productId").value(1))
                .andExpect(json("$.data.items[0].productName").value("키보드"))
                .andExpect(json("$.data.items[0].unitPrice").value(30000))
                .andExpect(json("$.data.items[0].quantity").value(2))
                .andExpect(json("$.data.items[1].productName").value("마우스"))
                .andExpect(json("$.data.createdAt").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));

        stock.verify();
        Order order = orderRepository.findAll().getFirst();
        assertThat(order.getMemberId()).isEqualTo(10L);
        assertThat(order.getOrderKey()).isEqualTo(orderKey.get());
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("order.events");
            assertThat(message.getMessageKey()).isEqualTo(String.valueOf(order.getId()));
            assertThat(message.getMessageType()).isEqualTo("OrderCreated");
            assertThat(message.getPayload()).isEqualTo(("{\"orderId\":%d,\"orderKey\":\"%s\",\"items\":["
                    + "{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":1}]}")
                    .formatted(order.getId(), orderKey.get()));
        });
    }

    @Test
    @DisplayName("X-Member-Id 가 없으면 400 VALIDATION_ERROR 이고 stock 을 부르지 않는다")
    void missingMemberId() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }"))
                .andExpect(status().isBadRequest())
                .andExpect(json("$.errorCode").value("VALIDATION_ERROR"));

        stock.verify();
        assertThat(orderRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{ \"items\": [] }",
            "{ \"items\": [{ \"quantity\": 1 }] }",
            "{ \"items\": [{ \"productId\": 1 }] }",
            "{ \"items\": [{ \"productId\": 1, \"quantity\": 0 }] }",
            "{ \"items\": [{ \"productId\": 1, \"quantity\": 11 }] }",
            "{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }, { \"productId\": 1, \"quantity\": 2 }] }"
    })
    @DisplayName("items 누락·빈 목록, productId·quantity 누락, 수량 1 ~ 10 밖, 같은 상품 중복은 400 이고 stock 을 부르지 않는다")
    void invalidItems(String body) throws Exception {
        order(body)
                .andExpect(status().isBadRequest())
                .andExpect(json("$.errorCode").value("VALIDATION_ERROR"));

        stock.verify();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("상품이 21개면 400 이다")
    void tooManyItems() throws Exception {
        String items = IntStream.rangeClosed(1, 21)
                .mapToObj(id -> "{ \"productId\": %d, \"quantity\": 1 }".formatted(id))
                .collect(Collectors.joining(","));

        order("{ \"items\": [" + items + "] }")
                .andExpect(status().isBadRequest())
                .andExpect(json("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("스냅샷이 없는 상품이 있으면 404 PRODUCT_NOT_FOUND 이고 stock 을 부르지 않는다")
    void productNotFound() throws Exception {
        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }, { \"productId\": 99, \"quantity\": 1 }] }")
                .andExpect(status().isNotFound())
                .andExpect(json("$.errorCode").value("PRODUCT_NOT_FOUND"));

        stock.verify();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("stock 이 409 면 409 OUT_OF_STOCK 이고 주문을 저장하지 않으며 restore 를 보내지 않는다")
    void outOfStock() throws Exception {
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/decrease")).andRespond(withRequestConflict());

        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }")
                .andExpect(status().isConflict())
                .andExpect(json("$.errorCode").value("OUT_OF_STOCK"));

        stock.verify();
        assertNothingSaved();
    }

    @Test
    @DisplayName("stock 응답이 read timeout 이면 503 이고, 차감됐을 수 있으므로 같은 orderKey 로 restore 를 보낸다")
    void readTimeout() throws Exception {
        AtomicReference<String> orderKey = new AtomicReference<>();
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/decrease"))
                .andExpect(request -> orderKey.set(
                        request.getBody().toString().replaceAll(".*\"orderKey\":\"([^\"]+)\".*", "$1")))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/restore"))
                .andExpect(request -> assertThat(request.getBody().toString())
                        .isEqualTo("{\"orderKey\":\"" + orderKey.get() + "\"}"))
                .andRespond(withSuccess());

        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }")
                .andExpect(status().isServiceUnavailable())
                .andExpect(json("$.errorCode").value("STOCK_SERVICE_UNAVAILABLE"));

        stock.verify();
        assertNothingSaved();
    }

    @Test
    @DisplayName("stock 에 연결하지 못하면 503 이고 restore 를 보내지 않는다")
    void connectionRefused() throws Exception {
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/decrease"))
                .andRespond(withException(new ConnectException("Connection refused")));

        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }")
                .andExpect(status().isServiceUnavailable())
                .andExpect(json("$.errorCode").value("STOCK_SERVICE_UNAVAILABLE"));

        stock.verify();
        assertNothingSaved();
    }

    @Test
    @DisplayName("stock 이 5xx 면 503 이고 restore 를 보낸다")
    void stockServerError() throws Exception {
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/decrease")).andRespond(withServerError());
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/restore")).andRespond(withSuccess());

        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }")
                .andExpect(status().isServiceUnavailable());

        stock.verify();
        assertNothingSaved();
    }

    @Test
    @DisplayName("차감 뒤 주문 저장에 실패하면 주문과 이벤트를 남기지 않고 restore 를 보낸다")
    void saveFailure() throws Exception {
        doThrow(new IllegalStateException("저장 실패")).when(orderRepository).save(any());
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/decrease")).andRespond(withSuccess());
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/restore")).andRespond(withSuccess());

        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }")
                .andExpect(status().isInternalServerError());

        stock.verify();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("restore 가 5xx 로 실패하면 다시 시도한다")
    void restoreRetried() throws Exception {
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/decrease"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        stock.expect(times(2), requestTo(STOCK + "/internal/stocks/restore")).andRespond(withServiceUnavailable());
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/restore")).andRespond(withSuccess());

        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }")
                .andExpect(status().isServiceUnavailable());

        stock.verify();
    }

    @Test
    @DisplayName("restore 재시도를 모두 실패하면 orderKey 와 상품을 ERROR 로그로 남긴다")
    void restoreExhausted(CapturedOutput output) throws Exception {
        stock.expect(once(), requestTo(STOCK + "/internal/stocks/decrease"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        stock.expect(times(6), requestTo(STOCK + "/internal/stocks/restore")).andRespond(withServiceUnavailable());

        order("{ \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }")
                .andExpect(status().isServiceUnavailable());

        stock.verify();
        assertThat(output).contains("재고 복원을 끝내 실패했습니다").contains("productId=1");
    }

    private void assertNothingSaved() {
        assertThat(orderRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    private ResultActions order(String body) throws Exception {
        return mockMvc.perform(post("/api/orders").header("X-Member-Id", 10L)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static org.springframework.test.web.servlet.result.JsonPathResultMatchers json(String path) {
        return MockMvcResultMatchers.jsonPath(path);
    }
}
