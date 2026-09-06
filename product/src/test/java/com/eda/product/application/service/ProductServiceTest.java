package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.product.application.port.in.CreateProductCommand;
import com.eda.product.application.port.out.SaveOutboxPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private SaveProductPort saveProductPort;

    @Mock
    private SaveOutboxPort saveOutboxPort;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품을 저장한 뒤 상품 생성 이벤트를 발행한다.")
    void createProduct() {
        // given
        CreateProductCommand command = new CreateProductCommand(
                "티셔츠",
                BigDecimal.valueOf(10000)
        );

        Product savedProduct = org.mockito.Mockito.mock(Product.class);

        when(savedProduct.getId()).thenReturn(1L);
        when(savedProduct.getName()).thenReturn("티셔츠");
        when(savedProduct.getPrice()).thenReturn(BigDecimal.valueOf(10000));

        when(saveProductPort.save(any(Product.class)))
                .thenReturn(savedProduct);

        // when
        productService.create(command);

        // then

        ArgumentCaptor<ProductCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProductCreatedEvent.class);

        InOrder order = inOrder(
                saveProductPort,
                saveOutboxPort
        );

        order.verify(saveProductPort)
                .save(any(Product.class));

        order.verify(saveOutboxPort)
                .save(eventCaptor.capture());

        ProductCreatedEvent event = eventCaptor.getValue();

        assertThat(event.eventId()).isNotNull();
        assertThat(event.productId()).isEqualTo(1L);
        assertThat(event.name()).isEqualTo("티셔츠");
        assertThat(event.price()).isEqualByComparingTo("10000");
        assertThat(event.occurredAt()).isNotNull();

    }

    @Test
    @DisplayName("상품 정보가 유효하지 않으면 저장하거나 이벤트를 발행하지 않는다.")
    void failToCreateInvalidProduct() {
        // given
        CreateProductCommand command = new CreateProductCommand(
                " ",
                BigDecimal.valueOf(10000)
        );

        // when
        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> productService.create(command)
        );

        // then
        assertThat(exception).isNotNull();

        verifyNoInteractions(
                saveProductPort,
                saveOutboxPort
        );
    }
}
