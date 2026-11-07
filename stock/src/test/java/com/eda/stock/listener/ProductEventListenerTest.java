package com.eda.stock.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eda.common.message.MessageHeader;
import com.eda.stock.cache.StockCache;
import com.eda.stock.exception.InvalidProductEventException;
import com.eda.stock.service.StockService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ProductEventListenerTest {

    private final StockService stockService = mock(StockService.class);
    private final StockCache stockCache = mock(StockCache.class);
    private final ProductEventListener listener = new ProductEventListener(JsonMapper.builder().build(), stockService, stockCache);

    @Test
    @DisplayName("상품 생성 이벤트를 변환하고 식별자를 서비스에 전달한다")
    void receive() {
        UUID messageId = UUID.randomUUID();
        var record = record(messageId, "{\"productId\":1,\"name\":\"키보드\",\"price\":30000}");
        when(stockService.createFor(messageId, 1L)).thenReturn(true);

        listener.on(record);

        var order = inOrder(stockService, stockCache);
        order.verify(stockService).createFor(messageId, 1L);
        order.verify(stockCache).initialize(1L);
    }

    @Test
    @DisplayName("이미 처리한 메시지나 기존 재고면 Redis 초기화를 건너뛴다")
    void existingStock() {
        UUID messageId = UUID.randomUUID();
        var record = record(messageId, "{\"productId\":1,\"name\":\"키보드\",\"price\":30000}");
        when(stockService.createFor(messageId, 1L)).thenReturn(false);

        listener.on(record);

        verify(stockService).createFor(messageId, 1L);
        verifyNoInteractions(stockCache);
    }

    @Test
    @DisplayName("다른 종류의 이벤트는 본문을 변환하지 않고 무시한다")
    void ignoreOtherType() {
        var record = new ConsumerRecord<String, String>("product.events", 0, 0L, "1", "not json");
        record.headers().add(MessageHeader.MESSAGE_TYPE, "ProductUpdated".getBytes(StandardCharsets.UTF_8));

        listener.on(record);

        verifyNoInteractions(stockService, stockCache);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"productId\":\"abc\"}"})
    @DisplayName("잘못된 본문은 재시도 불가능한 예외로 전파한다")
    void malformedPayload(String payload) {
        var record = record(UUID.randomUUID(), payload);

        assertThatThrownBy(() -> listener.on(record)).isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(stockService, stockCache);
    }

    @ParameterizedTest
    @ValueSource(strings = {"messageId", "messageType", "occurredAt"})
    @DisplayName("필수 헤더가 없으면 재시도 불가능한 예외로 전파한다")
    void missingHeader(String header) {
        var record = record(UUID.randomUUID(), "{\"productId\":1}");
        record.headers().remove(header);

        assertThatThrownBy(() -> listener.on(record)).isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(stockService, stockCache);
    }

    @Test
    @DisplayName("서비스 실패는 형식 오류로 바꾸지 않고 전파한다")
    void serviceFailure() {
        UUID messageId = UUID.randomUUID();
        var failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(stockService).createFor(messageId, 1L);
        var record = record(messageId, "{\"productId\":1,\"name\":\"키보드\",\"price\":30000}");

        assertThatThrownBy(() -> listener.on(record)).isSameAs(failure);
        verifyNoInteractions(stockCache);
    }

    private ConsumerRecord<String, String> record(UUID messageId, String payload) {
        var record = new ConsumerRecord<String, String>("product.events", 0, 0L, "1", payload);
        record.headers().add(MessageHeader.MESSAGE_ID, messageId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.MESSAGE_TYPE, "ProductCreated".getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.OCCURRED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
