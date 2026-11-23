package com.eda.order.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.eda.common.exception.ErrorCode;
import com.eda.order.config.StockClientProperties;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderLine;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Try 응답별 재시도 판단 — 결과를 모를 때만 같은 reservationId 로 다시 보낸다
class StockClientTest {

    private static final String RESERVE_URL = "http://stock/internal/stocks/reservations";

    private MockRestServiceServer server;
    private StockClient stockClient;
    private UUID reservationId;
    private List<OrderLine> orderLines;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://stock");
        server = MockRestServiceServer.bindTo(builder).build();
        StockClientProperties properties = new StockClientProperties(
                "http://stock", Duration.ofSeconds(1), Duration.ofSeconds(2), 3, Duration.ZERO);
        stockClient = new StockClient(builder.build(), properties);

        Order order = Order.create(1L, List.of(new Order.LineItem(1L, "키보드", BigDecimal.valueOf(30_000), 2)));
        reservationId = order.getReservationId();
        orderLines = order.getOrderLines();
    }

    @Test
    @DisplayName("예약 요청에 reservationId 와 상품별 수량을 담는다")
    void reserve() {
        // given
        server.expect(once(), requestTo(RESERVE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andRespond(withSuccess());

        // when & then
        assertThatCode(() -> stockClient.reserve(reservationId, orderLines)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("409 면 재시도하지 않고 OUT_OF_STOCK (이번 시도에서 바뀐 것이 없음)")
    void outOfStock() {
        // given
        server.expect(once(), requestTo(RESERVE_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        // when & then
        assertThatThrownBy(() -> stockClient.reserve(reservationId, orderLines))
                .isInstanceOfSatisfying(StockReservationFailedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
                    assertThat(e.isOutcomeUnknown()).isFalse();
                });
        server.verify();
    }

    @Test
    @DisplayName("그 밖의 4xx 도 재시도하지 않고 STOCK_SERVICE_UNAVAILABLE")
    void otherClientError() {
        // given
        server.expect(once(), requestTo(RESERVE_URL)).andRespond(withStatus(HttpStatus.GONE));

        // when & then
        assertThatThrownBy(() -> stockClient.reserve(reservationId, orderLines))
                .isInstanceOfSatisfying(StockReservationFailedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STOCK_SERVICE_UNAVAILABLE);
                    assertThat(e.isOutcomeUnknown()).isFalse();
                });
        server.verify();
    }

    @Test
    @DisplayName("타임아웃 뒤 같은 reservationId 로 다시 보내 성공하면 정상 처리한다")
    void retryAfterTimeout() {
        // given
        server.expect(once(), requestTo(RESERVE_URL))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        server.expect(once(), requestTo(RESERVE_URL))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andRespond(withSuccess());

        // when & then
        assertThatCode(() -> stockClient.reserve(reservationId, orderLines)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("5xx 도 결과를 모르는 것으로 보고 재시도한다")
    void retryAfterServerError() {
        // given
        server.expect(once(), requestTo(RESERVE_URL)).andRespond(withServerError());
        server.expect(once(), requestTo(RESERVE_URL)).andRespond(withSuccess());

        // when & then
        assertThatCode(() -> stockClient.reserve(reservationId, orderLines)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("최대 3번 모두 결과를 모르면 outcomeUnknown 으로 STOCK_SERVICE_UNAVAILABLE")
    void giveUpAfterMaxAttempts() {
        // given
        server.expect(times(3), requestTo(RESERVE_URL))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        // when & then
        assertThatThrownBy(() -> stockClient.reserve(reservationId, orderLines))
                .isInstanceOfSatisfying(StockReservationFailedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STOCK_SERVICE_UNAVAILABLE);
                    assertThat(e.isOutcomeUnknown()).isTrue();
                });
        server.verify();
    }

    @Test
    @DisplayName("타임아웃 뒤 재시도가 409 면 OUT_OF_STOCK 이지만 outcomeUnknown (앞선 시도가 늦게 실행될 수 있음)")
    void outOfStockAfterTimeout() {
        // given
        server.expect(once(), requestTo(RESERVE_URL))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        server.expect(once(), requestTo(RESERVE_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        // when & then
        assertThatThrownBy(() -> stockClient.reserve(reservationId, orderLines))
                .isInstanceOfSatisfying(StockReservationFailedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
                    assertThat(e.isOutcomeUnknown()).isTrue();
                });
        server.verify();
    }

    @Test
    @DisplayName("취소는 reservationId 경로로 요청한다")
    void cancel() {
        // given
        server.expect(once(), requestTo(RESERVE_URL + "/" + reservationId + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        // when
        stockClient.cancel(reservationId);

        // then
        server.verify();
    }
}
