package com.eda.product.api;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.product.api.dto.CreateProductRequest;
import com.eda.product.domain.Product;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class CreateProductRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1000000", "30000.00"})
    @DisplayName("이름과 가격이 허용 범위이면 검증을 통과한다")
    void validRequest(String price) {
        CreateProductRequest request = new CreateProductRequest("키보드", new BigDecimal(price));

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("상품명이 없거나 공백이면 name 필드에 오류가 생긴다")
    void blankName(String name) {
        CreateProductRequest request = new CreateProductRequest(name, BigDecimal.TEN);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("name");
    }

    @Test
    @DisplayName("상품명은 100자까지 허용하고 101자는 거부한다")
    void nameLength() {
        assertThat(validator.validate(new CreateProductRequest("가".repeat(100), BigDecimal.TEN))).isEmpty();
        assertThat(validator.validate(new CreateProductRequest("가".repeat(101), BigDecimal.TEN)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1000001"})
    @DisplayName("가격이 범위를 벗어나면 price 필드에 오류가 생긴다")
    void invalidPrice(String price) {
        CreateProductRequest request = new CreateProductRequest("키보드", new BigDecimal(price));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("price");
    }

    @Test
    @DisplayName("가격이 없으면 price 필드에 오류가 생긴다")
    void missingPrice() {
        CreateProductRequest request = new CreateProductRequest("키보드", null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("price");
    }

    @Test
    @DisplayName("JSON의 소수 가격은 잘리지 않고 도메인의 정수 검증에서 거부된다")
    void fractionalPriceIsPreserved() {
        CreateProductRequest request = JsonMapper.builder().build().readValue(
                """
                {"name":"키보드","price":100.5}
                """, CreateProductRequest.class);

        assertThat(request.price()).isEqualByComparingTo("100.5");
        assertThatThrownBy(() -> Product.register(request.name(), request.price()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
