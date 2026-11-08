package com.eda.order.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eda.common.message.MessageHeader;
import com.eda.common.message.product.ProductCreated;
import com.eda.order.exception.InvalidProductEventException;
import com.eda.order.service.ProductSnapshotService;
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

    private final ProductSnapshotService service = mock(ProductSnapshotService.class);
    private final ProductEventListener listener = new ProductEventListener(JsonMapper.builder().build(), service);

    @Test
    @DisplayName("상품 이벤트를 변환하고 메시지 ID와 전체 상품 정보를 서비스로 전달한다")
    void on_shouldForwardProduct_whenEventValid() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(id, "{\"productId\":1,\"name\":\"키보드\",\"price\":30000}");

        listener.on(record);

        verify(service).createFor(id, new ProductCreated(1L, "키보드", 30000));
    }

    @Test
    @DisplayName("다른 이벤트 타입이면 잘못된 본문도 변환하지 않고 건너뛴다")
    void on_shouldSkip_whenEventTypeUnrelated() {
        ConsumerRecord<String, String> record = record(UUID.randomUUID(), "not json");
        record.headers().remove(MessageHeader.MESSAGE_TYPE);
        record.headers().add(MessageHeader.MESSAGE_TYPE, "ProductUpdated".getBytes(StandardCharsets.UTF_8));

        listener.on(record);

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"messageId", "messageType", "occurredAt"})
    @DisplayName("필수 헤더가 없으면 형식 오류로 전파한다")
    void on_shouldReject_whenHeaderMissing(String header) {
        ConsumerRecord<String, String> record = record(UUID.randomUUID(), "{\"productId\":1,\"name\":\"키보드\",\"price\":30000}");
        record.headers().remove(header);

        assertThatThrownBy(() -> listener.on(record)).isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"productId\":\"abc\"}"})
    @DisplayName("잘못된 JSON과 null 본문은 형식 오류로 전파한다")
    void on_shouldReject_whenPayloadMalformed(String payload) {
        ConsumerRecord<String, String> record = record(UUID.randomUUID(), payload);

        assertThatThrownBy(() -> listener.on(record)).isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("DB 저장 실패를 메시지 형식 오류로 바꾸지 않고 그대로 전파한다")
    void on_shouldPropagate_whenSnapshotSaveFails() {
        UUID id = UUID.randomUUID();
        var event = new ProductCreated(1L, "키보드", 30000);
        var failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(service).createFor(id, event);
        ConsumerRecord<String, String> record = record(id, "{\"productId\":1,\"name\":\"키보드\",\"price\":30000}");

        assertThatThrownBy(() -> listener.on(record)).isSameAs(failure);
    }

    private ConsumerRecord<String, String> record(UUID id, String payload) {
        var record = new ConsumerRecord<String, String>("product.events", 0, 0L, "1", payload);
        record.headers().add(MessageHeader.MESSAGE_ID, id.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.MESSAGE_TYPE, "ProductCreated".getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.OCCURRED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
