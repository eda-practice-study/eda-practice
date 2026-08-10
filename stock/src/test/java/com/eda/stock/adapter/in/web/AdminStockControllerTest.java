package com.eda.stock.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.in.AddStockUseCase;
import com.eda.stock.application.port.in.AddStockUseCase.AddStockCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminStockController.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class AdminStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddStockUseCase addStockUseCase;

    @Test
    @DisplayName("재고 등록에 성공하면 200과 누적 수량을 반환한다")
    void addStock() throws Exception {
        // given
        given(addStockUseCase.add(any(AddStockCommand.class))).willReturn(100);

        // when & then
        mockMvc.perform(post("/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"quantity":100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(1))
                .andExpect(jsonPath("$.data.quantity").value(100));
    }

    @Test
    @DisplayName("재고 row 가 없으면 404 UNKNOWN_PRODUCT 를 반환한다")
    void returnNotFoundForUnknownProduct() throws Exception {
        // given
        willThrow(new BusinessException(ErrorCode.UNKNOWN_PRODUCT))
                .given(addStockUseCase).add(any(AddStockCommand.class));

        // when & then
        mockMvc.perform(post("/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":999,"quantity":10}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_PRODUCT"));
    }

    @Test
    @DisplayName("수량이 0 이하이면 400과 필드별 에러를 반환한다")
    void failWhenQuantityIsNotPositive() throws Exception {
        // when & then
        mockMvc.perform(post("/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.quantity").value("수량은 0보다 커야 합니다"));
    }

    @Test
    @DisplayName("상품 ID가 없으면 400을 반환한다")
    void failWhenProductIdIsMissing() throws Exception {
        // when & then
        mockMvc.perform(post("/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productId").value("상품 ID는 필수입니다"));
    }
}
