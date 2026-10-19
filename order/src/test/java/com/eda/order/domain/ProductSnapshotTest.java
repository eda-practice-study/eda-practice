package com.eda.order.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductSnapshotTest {

    @Test
    @DisplayName("스냅샷으로 주문 항목을 만들면 상품명과 단가가 채워진다")
    void toLineItem() {
        // given
        ProductSnapshot snapshot = ProductSnapshot.of(1L, "키보드", BigDecimal.valueOf(30000));

        // when
        Order.LineItem item = snapshot.toLineItem(2);

        // then
        assertThat(item).isEqualTo(new Order.LineItem(1L, "키보드", BigDecimal.valueOf(30000), 2));
    }

    @Test
    @DisplayName("필수 값이 없으면 생성에 실패한다")
    void failToCreateWithoutRequired() {
        assertThatThrownBy(() -> ProductSnapshot.of(null, "키보드", BigDecimal.TEN))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> ProductSnapshot.of(1L, " ", BigDecimal.TEN))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
