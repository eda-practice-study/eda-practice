package com.eda.stock.application;

import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.application.dto.StockIncreaseResult;
import com.eda.stock.api.dto.IncreaseStockResponse;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("StockService 재고 증가 흐름")
class StockServiceTest {
    // 실제 DB·Redis 대신 동작을 지정할 수 있는 가짜 객체(mock)를 사용한다.
    // 이 테스트는 서비스의 호출 순서와 응답을 검증하며 실제 커밋 여부는 검증하지 않는다.
    private final StockCommandService commandService = mock(StockCommandService.class);
    private final StockRedisRepository redisRepository = mock(StockRedisRepository.class);
    private final StockService service = new StockService(
            mock(StockRepository.class), redisRepository, mock(OutboxWriter.class), commandService);

    @Test
    @DisplayName("DB 처리가 성공하면 Redis를 증가시키고 각 재고 수량을 반환한다")
    void increasesRedisAfterDbProcessing() {
        // given: DB·Redis 증가가 성공하는 상황을 준비한다.
        given(commandService.increase(1L, 10)).willReturn(new StockIncreaseResult(1L, 110));
        given(redisRepository.increase(1L, 10)).willReturn(110L);

        // when: 검증할 기능을 실행한다.
        IncreaseStockResponse response = service.increaseStock(1L, 10);

        // then: 반환값과 DB 처리 → Redis 처리 순서를 확인한다.
        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.quantity()).isEqualTo(110);
        assertThat(response.availableQuantity()).isEqualTo(110);
        InOrder order = inOrder(commandService, redisRepository);
        order.verify(commandService).increase(1L, 10);
        order.verify(redisRepository).increase(1L, 10);
    }

    @Test
    @DisplayName("Redis 증가가 실패해도 DB 처리 결과와 가용 재고 null을 반환한다")
    void redisFailureReturnsDbResultWithNullAvailability() {
        // given: DB 처리는 성공하지만 Redis는 접속 예외를 던진다.
        given(commandService.increase(1L, 10)).willReturn(new StockIncreaseResult(1L, 110));
        given(redisRepository.increase(1L, 10)).willThrow(new DataAccessResourceFailureException("Redis unavailable"));

        // when
        IncreaseStockResponse response = service.increaseStock(1L, 10);

        // then: Redis 예외가 호출자에게 전파되지 않고 null로 표현된다.
        assertThat(response.quantity()).isEqualTo(110);
        assertThat(response.availableQuantity()).isNull();
    }

    @Test
    @DisplayName("DB 처리가 실패하면 예외를 전달하고 Redis를 호출하지 않는다")
    void dbFailureDoesNotTouchRedis() {
        // given: DB 처리 단계에서 실패하도록 준비한다.
        given(commandService.increase(1L, 10)).willThrow(new IllegalStateException("DB failure"));

        // when: 발생한 예외를 받아 then 단계에서 확인한다.
        Throwable thrown = catchThrowable(() -> service.increaseStock(1L, 10));

        // then: DB 실패를 성공 응답으로 바꾸지 않고 Redis에도 접근하지 않는다.
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("DB failure");
        verifyNoInteractions(redisRepository);
    }

    @Test
    @DisplayName("Redis 재고 키가 없으면 가용 재고를 null로 반환한다")
    void missingRedisKeyReturnsNullAvailability() {
        // given: DB 처리는 성공하고 Redis 저장소는 키 없음(null)을 반환한다.
        given(commandService.increase(1L, 10)).willReturn(new StockIncreaseResult(1L, 110));
        given(redisRepository.increase(1L, 10)).willReturn(null);

        // when
        IncreaseStockResponse response = service.increaseStock(1L, 10);

        // then: 실제 재고 0과 재고 정보 없음(null)을 구분한다.
        assertThat(response.quantity()).isEqualTo(110);
        assertThat(response.availableQuantity()).isNull();
    }
}
