package com.eda.payment.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    @DisplayName("결제를 생성하면 PENDING 상태다")
    void create() {
        Payment payment = Payment.create(10L, 1L, BigDecimal.valueOf(45000));

        assertThat(payment.getOrderId()).isEqualTo(10L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("필수 값이 없거나 금액이 0 이하면 생성에 실패한다")
    void failWithInvalidInput() {
        assertThatThrownBy(() -> Payment.create(null, 1L, BigDecimal.TEN))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> Payment.create(10L, 1L, BigDecimal.ZERO))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
