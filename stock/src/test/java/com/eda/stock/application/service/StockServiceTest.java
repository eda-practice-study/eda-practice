package com.eda.stock.application.service;

import com.eda.common.exception.BusinessException;
import com.eda.stock.application.port.in.CreateStockCommand;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StockServiceTest {

    @Mock
    private SaveStockPort saveStockPort;

    @InjectMocks
    private StockService stockService;

    @Test
    @DisplayName("상품 ID로 수량이 0인 재고를 생성하고 저장한다.")
    void createStock() {
        // given
        CreateStockCommand command = new CreateStockCommand(1L);

        when(saveStockPort.save(any(Stock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Stock result = stockService.create(command);

        // then
        ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);

        verify(saveStockPort)
                .save(stockCaptor.capture());

        Stock savedStock = stockCaptor.getValue();

        assertThat(savedStock.getProductId()).isEqualTo(1L);
        assertThat(savedStock.getQuantity()).isZero();
        assertThat(result).isSameAs(savedStock);
    }

    @Test
    @DisplayName("상품 ID가 없으면 재고를 저장하지 않는다.")
    void failToCreateWithoutProductId() {
        // given
        CreateStockCommand command = new CreateStockCommand(null);

        // when & then
        assertThatThrownBy(() -> stockService.create(command))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(saveStockPort);
    }


}
