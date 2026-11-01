package com.eda.product.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.exception.GlobalExceptionHandler;
import com.eda.product.domain.Product;
import com.eda.product.exception.ProductNameDuplicatedException;
import com.eda.product.service.ProductService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTest {

    private final ProductService productService = mock(ProductService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("상품 등록 후 201과 Location 및 상품 정보를 응답한다")
    void create() throws Exception {
        Product product = Product.register("키보드", BigDecimal.valueOf(30000));
        ReflectionTestUtils.setField(product, "id", 1L);
        ReflectionTestUtils.setField(product, "createdAt", LocalDateTime.of(2026, 11, 1, 12, 0));
        when(productService.register("키보드", BigDecimal.valueOf(30000))).thenReturn(product);

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"키보드\",\"price\":30000}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/1"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.productId").value(1))
                .andExpect(jsonPath("$.data.name").value("키보드"))
                .andExpect(jsonPath("$.data.price").value(30000))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-01T12:00:00"));
        verify(productService).register("키보드", BigDecimal.valueOf(30000));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":\"키보드\"}",
            "{\"price\":30000}",
            "{\"name\":\" \",\"price\":30000}",
            "{\"name\":\"키보드\",\"price\":null}",
            "{\"name\":\"키보드\",\"price\":0}",
            "{\"name\":\"키보드\",\"price\":1000001}",
            "{\"name\":\"키보드\",\"price\":\"abc\"}",
            "{",
            ""
    })
    @DisplayName("필수값 누락, 범위 오류 및 잘못된 JSON은 서비스 호출 없이 400을 응답한다")
    void invalidRequest(String body) throws Exception {
        assertBadRequest(body);
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("상품명이 100자를 초과하면 400을 응답한다")
    void nameTooLong() throws Exception {
        assertBadRequest("{\"name\":\"" + "가".repeat(101) + "\",\"price\":30000}");
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("도메인의 원 단위 정수 검증 오류를 400으로 응답한다")
    void fractionalPrice() throws Exception {
        when(productService.register(any(), any())).thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        assertBadRequest("{\"name\":\"키보드\",\"price\":100.5}");
        verify(productService).register("키보드", new BigDecimal("100.5"));
    }

    @Test
    @DisplayName("상품명 중복은 409를 응답한다")
    void duplicateName() throws Exception {
        when(productService.register(any(), any())).thenThrow(new ProductNameDuplicatedException());

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"키보드\",\"price\":30000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NAME_DUPLICATED"));
    }

    private void assertBadRequest(String body) throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
