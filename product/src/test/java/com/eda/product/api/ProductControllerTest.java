package com.eda.product.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.exception.GlobalExceptionHandler;
import com.eda.product.domain.Product;
import com.eda.product.exception.ProductControllerAdvice;
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
                .setControllerAdvice(new GlobalExceptionHandler(), new ProductControllerAdvice())
                .build();
    }

    @Test
    @DisplayName("상품 등록 후 201과 Location 및 상품 정보를 응답한다")
    void create_shouldReturnCreated_whenRequestValid() throws Exception {
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
    @ValueSource(strings = {"{}", "{\"name\":\"키보드\"}", "{\"price\":30000}", "{\"name\":\"키보드\",\"price\":null}"})
    @DisplayName("필수값이 없으면 서비스를 호출하지 않고 400을 응답한다")
    void create_shouldReject_whenRequiredFieldMissing(String body) throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("상품명이 공백이면 서비스를 호출하지 않고 400을 응답한다")
    void create_shouldReject_whenNameBlank() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"price\":30000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":\"키보드\",\"price\":0}", "{\"name\":\"키보드\",\"price\":1000001}"})
    @DisplayName("가격이 허용 범위를 벗어나면 서비스를 호출하지 않고 400을 응답한다")
    void create_shouldReject_whenPriceOutsideRange(String body) throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("가격을 숫자로 읽을 수 없으면 서비스를 호출하지 않고 400을 응답한다")
    void create_shouldReject_whenPriceNotNumeric() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"키보드\",\"price\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("JSON 형식이 잘못되면 서비스를 호출하지 않고 400을 응답한다")
    void create_shouldReject_whenJsonMalformed() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("본문이 없으면 서비스를 호출하지 않고 400을 응답한다")
    void create_shouldReject_whenBodyMissing() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("상품명이 100자를 초과하면 400을 응답한다")
    void create_shouldReject_whenNameTooLong() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + "가".repeat(101) + "\",\"price\":30000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("도메인의 원 단위 정수 검증 오류를 400으로 응답한다")
    void create_shouldReject_whenPriceFractional() throws Exception {
        when(productService.register(any(), any())).thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"키보드\",\"price\":100.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(productService).register("키보드", new BigDecimal("100.5"));
    }

    @Test
    @DisplayName("상품명 중복은 409를 응답한다")
    void create_shouldReturnConflict_whenNameAlreadyExists() throws Exception {
        when(productService.register(any(), any())).thenThrow(new ProductNameDuplicatedException());

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"키보드\",\"price\":30000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NAME_DUPLICATED"));
    }

}
