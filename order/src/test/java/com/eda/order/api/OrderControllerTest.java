package com.eda.order.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.exception.GlobalExceptionHandler;
import com.eda.order.application.OrderService;
import com.eda.order.application.OrderService.OrderItem;
import com.eda.order.domain.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("주문을 만들면 201 과 주문 시점의 상품 스냅샷 값을 응답한다")
    void create() throws Exception {
        // given
        Order order = Order.create(1L, List.of(
                new Order.LineItem(1L, "키보드", BigDecimal.valueOf(30_000), 2),
                new Order.LineItem(2L, "마우스", BigDecimal.valueOf(15_000), 1)));
        given(orderService.create(1L, List.of(new OrderItem(1L, 2), new OrderItem(2L, 1)))).willReturn(order);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Member-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"productId":1,"quantity":2},{"productId":2,"quantity":1}""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.totalAmount").value(75_000))
                .andExpect(jsonPath("$.data.items[0].productName").value("키보드"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(30_000))
                .andExpect(jsonPath("$.data.items[1].quantity").value(1));
    }

    @Test
    @DisplayName("X-Member-Id 가 없으면 400 VALIDATION_ERROR")
    void missingMemberId() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"productId\":1,\"quantity\":1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        then(orderService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("요청 바디 규칙을 어기면 400 VALIDATION_ERROR")
    void invalidBody(String description, String content) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-Member-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        then(orderService).shouldHaveNoInteractions();
    }

    static List<Arguments> invalidBodies() {
        String twentyOne = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "{\"productId\":" + i + ",\"quantity\":1}")
                .collect(Collectors.joining(","));
        return List.of(
                Arguments.of("items 누락", "{}"),
                Arguments.of("items 빈 배열", body("")),
                Arguments.of("items 21개", body(twentyOne)),
                Arguments.of("productId 누락", body("{\"quantity\":1}")),
                Arguments.of("quantity 누락", body("{\"productId\":1}")),
                Arguments.of("quantity 0", body("{\"productId\":1,\"quantity\":0}")),
                Arguments.of("quantity 11", body("{\"productId\":1,\"quantity\":11}")),
                Arguments.of("productId 중복", body("{\"productId\":1,\"quantity\":1},{\"productId\":1,\"quantity\":2}")),
                Arguments.of("JSON 형식 오류", "{\"items\":[")
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("businessErrors")
    @DisplayName("서비스 예외는 에러 코드의 상태로 응답한다")
    void businessError(ErrorCode errorCode, int status) throws Exception {
        // given
        given(orderService.create(anyLong(), anyList())).willThrow(new BusinessException(errorCode));

        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("X-Member-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"productId\":1,\"quantity\":1}")))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.errorCode").value(errorCode.name()));
    }

    static List<Arguments> businessErrors() {
        return List.of(
                Arguments.of(ErrorCode.PRODUCT_NOT_FOUND, 404),
                Arguments.of(ErrorCode.OUT_OF_STOCK, 409),
                Arguments.of(ErrorCode.STOCK_SERVICE_UNAVAILABLE, 503)
        );
    }

    private static String body(String items) {
        return "{\"items\":[" + items + "]}";
    }
}
