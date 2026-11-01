package com.eda.product.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductRepository;
import com.eda.product.exception.ProductNameDuplicatedException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @InjectMocks
    private ProductService productService;

    @ParameterizedTest
    @ValueSource(strings = {"1", "1000000", "30000.00"})
    @DisplayName("상품을 저장한 뒤 상품 ID를 키로 생성 이벤트를 기록하고 저장된 상품을 반환한다")
    void register(String price) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 1, 12, 0);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 1L);
            ReflectionTestUtils.setField(product, "createdAt", createdAt);
            return product;
        });

        Product result = productService.register("키보드", new BigDecimal(price));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        var inOrder = inOrder(productRepository, outboxWriter);
        inOrder.verify(productRepository).saveAndFlush(productCaptor.capture());
        inOrder.verify(outboxWriter).write(
                "product.events", "1", new ProductCreated(1L, "키보드", new BigDecimal(price).longValueExact()));
        assertThat(result).isSameAs(productCaptor.getValue());
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("키보드");
        assertThat(result.getPrice()).isEqualByComparingTo(price);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        verifyNoMoreInteractions(productRepository, outboxWriter);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("상품명이 없거나 공백이면 상품과 이벤트를 저장하지 않는다")
    void invalidName(String name) {
        assertValidationFailure(name, BigDecimal.TEN);
    }

    @Test
    @DisplayName("상품명이 100자를 초과하면 상품과 이벤트를 저장하지 않는다")
    void nameTooLong() {
        assertValidationFailure("가".repeat(101), BigDecimal.TEN);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "1000001", "100.5"})
    @DisplayName("가격이 없거나 범위를 벗어나거나 소수면 상품과 이벤트를 저장하지 않는다")
    void invalidPrice(String price) {
        assertValidationFailure("키보드", price == null ? null : new BigDecimal(price));
    }

    @Test
    @DisplayName("동시 요청으로 상품명 유일 제약을 위반하면 상품명 중복 예외로 변환한다")
    void duplicateName() {
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenThrow(constraintViolation(Product.NAME_UNIQUE_CONSTRAINT));

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN))
                .isInstanceOf(ProductNameDuplicatedException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED);
        verifyNoInteractions(outboxWriter);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"other_constraint"})
    @DisplayName("상품명 중복 이외의 무결성 오류는 그대로 전파한다")
    void otherConstraintViolation(String constraintName) {
        DataIntegrityViolationException failure = constraintViolation(constraintName);
        when(productRepository.saveAndFlush(any(Product.class))).thenThrow(failure);

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN)).isSameAs(failure);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("제약 정보가 없는 무결성 오류를 상품명 중복으로 처리하지 않는다")
    void integrityViolationWithoutConstraint() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unknown integrity error");
        when(productRepository.saveAndFlush(any(Product.class))).thenThrow(failure);

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN)).isSameAs(failure);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("상품 저장이 실패하면 이벤트를 기록하지 않고 오류를 전파한다")
    void productSaveFailure() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(productRepository.saveAndFlush(any(Product.class))).thenThrow(failure);

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN)).isSameAs(failure);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("이벤트 기록이 실패하면 트랜잭션이 롤백될 수 있도록 오류를 전파한다")
    void outboxWriteFailure() {
        Product savedProduct = Product.register("키보드", BigDecimal.TEN);
        ReflectionTestUtils.setField(savedProduct, "id", 1L);
        when(productRepository.saveAndFlush(any(Product.class))).thenReturn(savedProduct);
        var failure = new IllegalStateException("outbox write failed");
        when(outboxWriter.write("product.events", "1", new ProductCreated(1L, "키보드", 10L)))
                .thenThrow(failure);

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN)).isSameAs(failure);
        verify(productRepository).saveAndFlush(any(Product.class));
    }

    private void assertValidationFailure(String name, BigDecimal price) {
        assertThatThrownBy(() -> productService.register(name, price))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(productRepository, outboxWriter);
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException("constraint violation",
                new ConstraintViolationException("constraint violation", new SQLException("constraint violation"), constraintName));
    }
}
