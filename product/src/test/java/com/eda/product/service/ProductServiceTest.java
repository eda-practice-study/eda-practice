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
import java.time.LocalDateTime;
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
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 1L);
            ReflectionTestUtils.setField(product, "createdAt", createdAt);
            return product;
        });

        Product result = productService.register("키보드", new BigDecimal(price));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        var inOrder = inOrder(productRepository, outboxWriter);
        inOrder.verify(productRepository).existsByName("키보드");
        inOrder.verify(productRepository).save(productCaptor.capture());
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
        assertThatThrownBy(() -> productService.register(name, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(productRepository, outboxWriter);
    }

    @Test
    @DisplayName("상품명이 100자를 초과하면 상품과 이벤트를 저장하지 않는다")
    void nameTooLong() {
        String name = "가".repeat(101);

        assertThatThrownBy(() -> productService.register(name, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(productRepository, outboxWriter);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "1000001", "100.5"})
    @DisplayName("가격이 없거나 범위를 벗어나거나 소수면 상품과 이벤트를 저장하지 않는다")
    void invalidPrice(String price) {
        BigDecimal invalidPrice = price == null ? null : new BigDecimal(price);

        assertThatThrownBy(() -> productService.register("키보드", invalidPrice))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(productRepository, outboxWriter);
    }

    @Test
    @DisplayName("이미 존재하는 상품명이면 상품과 이벤트를 저장하지 않는다")
    void duplicateName() {
        when(productRepository.existsByName("키보드")).thenReturn(true);

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN))
                .isInstanceOf(ProductNameDuplicatedException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED);
        verify(productRepository).existsByName("키보드");
        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("사전 조회 후 발생한 무결성 오류는 예외 처리기로 전파한다")
    void integrityViolationAfterNameCheck() {
        var failure = new DataIntegrityViolationException("constraint violation");
        when(productRepository.save(any(Product.class))).thenThrow(failure);

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN)).isSameAs(failure);
        verify(productRepository).existsByName("키보드");
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("상품 저장이 실패하면 이벤트를 기록하지 않고 오류를 전파한다")
    void productSaveFailure() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(productRepository.save(any(Product.class))).thenThrow(failure);

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN)).isSameAs(failure);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("이벤트 기록이 실패하면 트랜잭션이 롤백될 수 있도록 오류를 전파한다")
    void outboxWriteFailure() {
        Product savedProduct = Product.register("키보드", BigDecimal.TEN);
        ReflectionTestUtils.setField(savedProduct, "id", 1L);
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        var failure = new IllegalStateException("outbox write failed");
        when(outboxWriter.write("product.events", "1", new ProductCreated(1L, "키보드", 10L)))
                .thenThrow(failure);

        assertThatThrownBy(() -> productService.register("키보드", BigDecimal.TEN)).isSameAs(failure);
        verify(productRepository).save(any(Product.class));
    }

}
