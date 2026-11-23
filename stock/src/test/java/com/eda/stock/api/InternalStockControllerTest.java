package com.eda.stock.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.exception.GlobalExceptionHandler;
import com.eda.stock.application.StockService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalStockControllerTest {

    private static final String ID = "3f0c1b2e-8a4d-4c7e-9b1a-2d5e6f7a8b9c";

    private final StockService stockService = mock(StockService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalStockController(stockService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Try — reservationId 와 요청 순서대로의 상품별 수량을 넘긴다")
    void reserve() throws Exception {
        // when
        mockMvc.perform(post("/internal/stocks/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"" + ID + "\"", "{\"productId\":2,\"quantity\":1},{\"productId\":1,\"quantity\":3}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // then
        Map<Long, Integer> expected = new LinkedHashMap<>();
        expected.put(2L, 1);
        expected.put(1L, 3);
        then(stockService).should().reserve(UUID.fromString(ID), expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("Try — 요청 바디 규칙을 어기면 400 VALIDATION_ERROR")
    void invalidBody(String description, String content) throws Exception {
        mockMvc.perform(post("/internal/stocks/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        then(stockService).shouldHaveNoInteractions();
    }

    static List<Arguments> invalidBodies() {
        String item = "{\"productId\":1,\"quantity\":1}";
        return List.of(
                Arguments.of("reservationId 누락", "{\"items\":[" + item + "]}"),
                Arguments.of("reservationId 가 UUID 가 아님", body("\"abc\"", item)),
                Arguments.of("items 빈 배열", body("\"" + ID + "\"", "")),
                Arguments.of("quantity 0", body("\"" + ID + "\"", "{\"productId\":1,\"quantity\":0}")),
                Arguments.of("productId 중복", body("\"" + ID + "\"", item + "," + item))
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("businessErrors")
    @DisplayName("Try — 재고 부족은 409, 취소된 예약 ID 는 410")
    void businessError(ErrorCode errorCode, int status) throws Exception {
        // given
        willThrow(new BusinessException(errorCode)).given(stockService).reserve(any(), anyMap());

        // when & then
        mockMvc.perform(post("/internal/stocks/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"" + ID + "\"", "{\"productId\":1,\"quantity\":1}")))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.errorCode").value(errorCode.name()));
    }

    static List<Arguments> businessErrors() {
        return List.of(
                Arguments.of(ErrorCode.OUT_OF_STOCK, 409),
                Arguments.of(ErrorCode.RESERVATION_CANCELLED, 410)
        );
    }

    @Test
    @DisplayName("Cancel — 경로의 reservationId 로 취소한다")
    void cancel() throws Exception {
        mockMvc.perform(post("/internal/stocks/reservations/{reservationId}/cancel", ID))
                .andExpect(status().isOk());

        then(stockService).should().cancel(UUID.fromString(ID));
    }

    private static String body(String reservationId, String items) {
        return "{\"reservationId\":" + reservationId + ",\"items\":[" + items + "]}";
    }
}
