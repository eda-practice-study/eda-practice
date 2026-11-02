package com.eda.stock.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class StockKafkaConfigTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("DLT 전송 결과가 실패이면 복구 성공으로 처리하지 않고 예외를 던진다")
    void failedSendResult() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of("bootstrap.servers", "localhost:9092"));
        when(template.getProducerFactory()).thenReturn(producerFactory);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var recoverer = new StockKafkaConfig().stockDeadLetterRecoverer(template);
        var record = new ConsumerRecord<String, String>("product.events", 0, 0L, "1", "payload");

        assertThatThrownBy(() -> recoverer.accept(record, null, new RuntimeException("processing failed")))
                .isInstanceOf(KafkaException.class);

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("stock.product.events.DLT");
        assertThat(captor.getValue().partition()).isNull();
        assertThat(captor.getValue().key()).isEqualTo("1");
        assertThat(captor.getValue().value()).isEqualTo("payload");
    }
}
