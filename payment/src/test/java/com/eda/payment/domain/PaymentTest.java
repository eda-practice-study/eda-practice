package com.eda.payment.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final String PAYMENT_KEY = "pk-1";
    private static final Long ORDER_ID = 1L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10000);

    @Test
    @DisplayName("결제를 생성하면 PENDING 상태가 된다")
    void createPendingPayment() {
        // when
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);

        // then
        assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(payment.getAmount()).isEqualByComparingTo("10000");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("결제키가 null 이면 생성에 실패한다")
    void failToCreateWhenPaymentKeyNull() {
        // when & then
        assertThatThrownBy(() -> Payment.create(null, ORDER_ID, AMOUNT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("결제키가 공백이면 생성에 실패한다")
    void failToCreateWhenPaymentKeyBlank() {
        // when & then
        assertThatThrownBy(() -> Payment.create("  ", ORDER_ID, AMOUNT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("주문 ID가 null 이면 생성에 실패한다")
    void failToCreateWhenOrderIdNull() {
        // when & then
        assertThatThrownBy(() -> Payment.create(PAYMENT_KEY, null, AMOUNT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("금액이 null 이면 생성에 실패한다")
    void failToCreateWhenAmountNull() {
        // when & then
        assertThatThrownBy(() -> Payment.create(PAYMENT_KEY, ORDER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("금액이 0 이면 생성에 실패한다")
    void failToCreateWhenAmountZero() {
        // when & then
        assertThatThrownBy(() -> Payment.create(PAYMENT_KEY, ORDER_ID, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("금액이 음수이면 생성에 실패한다")
    void failToCreateWhenAmountNegative() {
        // when & then
        assertThatThrownBy(() -> Payment.create(PAYMENT_KEY, ORDER_ID, BigDecimal.valueOf(-1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("PENDING 결제를 승인하면 PAID가 된다")
    void completeToPaid() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);

        // when
        payment.complete();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("PENDING 결제를 실패 처리하면 FAILED가 된다")
    void failToFailed() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);

        // when
        payment.fail();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("PENDING 이 아니면 승인할 수 없다")
    void failToCompleteWhenNotPending() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);
        payment.complete();

        // when & then
        assertThatThrownBy(payment::complete)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);
    }

    @Test
    @DisplayName("PENDING 이 아니면 실패 처리할 수 없다")
    void failToFailWhenNotPending() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);
        payment.complete();

        // when & then
        assertThatThrownBy(payment::fail)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);
    }

    @Test
    @DisplayName("일부 금액을 환불하면 PARTIALLY_REFUNDED 상태이고 환불액이 누적된다")
    void partialRefund() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);
        payment.complete();

        // when
        payment.refund(BigDecimal.valueOf(3000));

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("여러 번에 걸쳐 전액을 환불하면 REFUNDED 상태가 된다")
    void fullRefund() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);
        payment.complete();

        // when
        payment.refund(BigDecimal.valueOf(4000));
        payment.refund(BigDecimal.valueOf(6000));

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("결제 금액을 초과하여 환불하면 실패한다")
    void failToRefundMoreThanAmount() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);
        payment.complete();

        // when & then
        assertThatThrownBy(() -> payment.refund(BigDecimal.valueOf(12000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFUND);
    }

    @Test
    @DisplayName("환불 금액이 null 이면 실패한다")
    void failToRefundWhenAmountNull() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);
        payment.complete();

        // when & then
        assertThatThrownBy(() -> payment.refund(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFUND);
    }

    @Test
    @DisplayName("환불 금액이 0 이면 실패한다")
    void failToRefundWhenAmountZero() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);
        payment.complete();

        // when & then
        assertThatThrownBy(() -> payment.refund(BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFUND);
    }

    @Test
    @DisplayName("환불 금액이 음수이면 실패한다")
    void failToRefundWhenAmountNegative() {
        // given
        Payment payment = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);
        payment.complete();

        // when & then
        assertThatThrownBy(() -> payment.refund(BigDecimal.valueOf(-1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFUND);
    }

    @Test
    @DisplayName("PAID 가 아닌 결제는 환불할 수 없다")
    void failToRefundWhenNotPaid() {
        // given
        Payment pending = Payment.create(PAYMENT_KEY, ORDER_ID, AMOUNT);

        // when & then
        assertThatThrownBy(() -> pending.refund(BigDecimal.valueOf(1000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFUND);
    }
}
