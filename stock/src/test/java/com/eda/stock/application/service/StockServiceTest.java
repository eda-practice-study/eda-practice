package com.eda.stock.application.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.in.AddStockCommand;
import com.eda.stock.application.port.in.CreateStockCommand;
import com.eda.stock.application.port.out.LoadStockForUpdatePort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StockServiceTest {

    @Mock
    private SaveStockPort saveStockPort;

    @Mock
    private LoadStockForUpdatePort loadStockForUpdatePort;

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

    @Test
    @DisplayName("기존 재고에 요청 수량을 추가한다")
    void addStock() {
        // given
        Stock stock = Stock.createFor(1L);
        stock.add(10);

        AddStockCommand command = new AddStockCommand(1L, 5);

        when(loadStockForUpdatePort.loadByProductIdForUpdate(1L))
                .thenReturn(Optional.of(stock));

        when(saveStockPort.save(stock))
                .thenReturn(stock);

        // when
        Stock result = stockService.add(command);

        // then
        assertThat(result.getQuantity()).isEqualTo(15);

        verify(loadStockForUpdatePort)
                .loadByProductIdForUpdate(1L);

        verify(saveStockPort)
                .save(stock);
    }

    @Test
    @DisplayName("재고 정보가 없는 상품에는 재고를 추가할 수 없다")
    void failToAddWhenStockDoesNotExist() {
        // given
        AddStockCommand command = new AddStockCommand(1L, 5);

        when(loadStockForUpdatePort.loadByProductIdForUpdate(1L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stockService.add(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STOCK_NOT_FOUND);

        verify(saveStockPort, never())
                .save(any());
    }

    @Test
    @DisplayName("0 이하 수량은 재고에 추가할 수 없다")
    void failToAddNonPositiveQuantity() {
        // given
        Stock stock = Stock.createFor(1L);
        AddStockCommand command = new AddStockCommand(1L, 0);

        when(loadStockForUpdatePort.loadByProductIdForUpdate(1L))
                .thenReturn(Optional.of(stock));

        // when & then
        assertThatThrownBy(() -> stockService.add(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STOCK_OPERATION);

        verify(saveStockPort, never())
                .save(any());
    }
}
