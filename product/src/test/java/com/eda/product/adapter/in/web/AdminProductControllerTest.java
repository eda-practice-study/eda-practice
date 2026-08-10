package com.eda.product.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.product.application.port.in.RegisterProductUseCase;
import com.eda.product.application.port.in.RegisterProductUseCase.RegisterProductCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminProductController.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterProductUseCase registerProductUseCase;

    @Test
    @DisplayName("상품 생성에 성공하면 201과 생성된 ID를 반환한다")
    void registerProduct() throws Exception {
        // given
        given(registerProductUseCase.register(any(RegisterProductCommand.class))).willReturn(1L);

        // when & then
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"티셔츠","price":10000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(1));
    }

    @Test
    @DisplayName("상품명이 비어 있으면 400과 필드별 에러를 반환한다")
    void failWhenNameIsBlank() throws Exception {
        // when & then
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  ","price":10000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").value("상품명은 필수입니다"));
    }

    @Test
    @DisplayName("가격이 0 이하이면 400을 반환한다")
    void failWhenPriceIsNotPositive() throws Exception {
        // when & then
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"티셔츠","price":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").value("가격은 0보다 커야 합니다"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 500이 아니라 405를 반환한다")
    void returnMethodNotAllowedInsteadOfServerError() throws Exception {
        // when & then
        mockMvc.perform(get("/admin/products"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Content-Type 이 JSON 이 아니면 415를 반환한다")
    void returnUnsupportedMediaType() throws Exception {
        // when & then
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("티셔츠"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}