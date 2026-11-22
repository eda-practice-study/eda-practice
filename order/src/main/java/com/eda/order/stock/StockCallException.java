package com.eda.order.stock;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;

// stock 호출이 실패했다. mayHaveDecreased 면 stock 이 Redis 재고를 이미 차감했을 수 있어 되돌려야 한다.
public class StockCallException extends BusinessException {

    private final boolean mayHaveDecreased;

    public StockCallException(boolean mayHaveDecreased) {
        super(ErrorCode.STOCK_SERVICE_UNAVAILABLE);
        this.mayHaveDecreased = mayHaveDecreased;
    }

    public boolean mayHaveDecreased() {
        return mayHaveDecreased;
    }
}
