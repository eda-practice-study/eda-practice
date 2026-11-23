package com.eda.order.infrastructure;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import lombok.Getter;

// 재고 예약(Try) 실패. outcomeUnknown = 한 번이라도 결과를 모르는 시도가 있었음
// → stock 에서 늦게 실행될 수 있으므로 예약 취소(Cancel)를 요청해야 한다
@Getter
public class StockReservationFailedException extends BusinessException {

    private final boolean outcomeUnknown;

    public StockReservationFailedException(ErrorCode errorCode, boolean outcomeUnknown) {
        super(errorCode);
        this.outcomeUnknown = outcomeUnknown;
    }
}
