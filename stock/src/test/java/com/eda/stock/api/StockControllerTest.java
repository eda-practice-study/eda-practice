package com.eda.stock.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.exception.GlobalExceptionHandler;
import com.eda.stock.exception.StockControllerAdvice;
import com.eda.stock.service.StockIncreaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import java.sql.SQLException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StockControllerTest {
    private final StockIncreaseService service = mock(StockIncreaseService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StockController(service))
                .setControllerAdvice(new StockControllerAdvice(), new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("재고 증가에 성공하면 DB 수량과 Redis 가용 재고를 200으로 응답한다")
    void increase_shouldReturnStock_whenRequestValid() throws Exception {
        when(service.increaseForRequest("key", 1L, 100)).thenReturn(new StockIncreaseResponse(1L, 100, 100L));

        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.productId").value(1))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.availableQuantity").value(100));
    }

    @Test
    @DisplayName("Redis 반영 결과가 없으면 200과 가용 재고 null을 응답한다")
    void increase_shouldReturnNullAvailability_whenRedisFails() throws Exception {
        when(service.increaseForRequest("key", 1L, 100)).thenReturn(new StockIncreaseResponse(1L, 100, null));

        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.availableQuantity").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"quantity\":null}", "{\"quantity\":0}", "{\"quantity\":-1}", "{\"quantity\":10001}"})
    @DisplayName("수량이 없거나 범위를 벗어나면 400을 응답하고 서비스를 호출하지 않는다")
    void increase_shouldReturnValidationError_whenQuantityInvalid(String body) throws Exception {
        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("멱등성 키가 없으면 400을 응답하고 서비스를 호출하지 않는다")
    void increase_shouldReturnValidationError_whenKeyMissing() throws Exception {
        mvc.perform(post("/api/stocks/1/increase").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("재고가 없으면 404를 응답한다")
    void increase_shouldReturnStockNotFound_whenStockMissing() throws Exception {
        when(service.increaseForRequest("key", 1L, 100)).thenThrow(new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 처리한 멱등성 키의 유일 제약 충돌이면 409를 응답한다")
    void increase_shouldReturnIdempotencyKeyConflict_whenKeyAlreadyProcessed() throws Exception {
        // given
        var cause = new ConstraintViolationException("duplicate key", new SQLException(), "uk_stock_increase_key");
        when(service.increaseForRequest("key", 1L, 100))
                .thenThrow(new DataIntegrityViolationException("duplicate key", cause));

        // when, then
        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_STOCK_INCREASE_REQUEST"));
    }

    @Test
    @DisplayName("다른 DB 제약 위반은 멱등성 키 충돌로 바꾸지 않고 500을 응답한다")
    void increase_shouldReturnInternalError_whenOtherConstraintFails() throws Exception {
        // given
        var cause = new ConstraintViolationException("check failed", new SQLException(), "other_constraint");
        when(service.increaseForRequest("key", 1L, 100))
                .thenThrow(new DataIntegrityViolationException("check failed", cause));

        // when, then
        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("낙관적 락 충돌이면 409를 응답한다")
    void increase_shouldReturnStockConflict_whenOptimisticLockFails() throws Exception {
        when(service.increaseForRequest("key", 1L, 100)).thenThrow(new ObjectOptimisticLockingFailureException("Stock", 1L));

        mvc.perform(post("/api/stocks/1/increase").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_INCREASE_CONFLICT"));
    }
}
