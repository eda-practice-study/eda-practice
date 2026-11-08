package com.eda.product.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eda.common.exception.GlobalExceptionHandler;
import com.eda.product.api.ProductController;
import com.eda.product.service.ProductService;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerAdviceTest {

    private final ProductService productService = mock(ProductService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ProductControllerAdvice())
                .build();
    }

    @Test
    @DisplayName("상품명 유일 제약 위반은 공통 예외 처리기보다 우선하여 409로 응답한다")
    void handleDataIntegrityViolation_shouldReturnConflict_whenNameConstraintViolated() throws Exception {
        var constraint = new ConstraintViolationException(
                "duplicate name", new SQLException("duplicate key", "23505"), "uk_product_name");
        var failure = new DataIntegrityViolationException("save failed", new RuntimeException(constraint));
        when(productService.register(any(), any())).thenThrow(failure);

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"키보드\",\"price\":30000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NAME_DUPLICATED"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"other_constraint"})
    @DisplayName("다른 제약 위반이나 제약 이름이 없는 오류는 500으로 응답한다")
    void handleDataIntegrityViolation_shouldReturnError_whenConstraintUnrecognized(String constraintName) throws Exception {
        var constraint = new ConstraintViolationException(
                "constraint violation", new SQLException("constraint violation"), constraintName);
        when(productService.register(any(), any()))
                .thenThrow(new DataIntegrityViolationException("save failed", constraint));

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"키보드\",\"price\":30000}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("원인 예외가 없는 무결성 오류는 500으로 응답한다")
    void handleDataIntegrityViolation_shouldReturnError_whenCauseMissing() throws Exception {
        when(productService.register(any(), any()))
                .thenThrow(new DataIntegrityViolationException("unknown integrity error"));

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"키보드\",\"price\":30000}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }
}
