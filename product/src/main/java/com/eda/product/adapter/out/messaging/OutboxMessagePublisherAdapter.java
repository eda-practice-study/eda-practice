package com.eda.product.adapter.out.messaging;

import com.eda.product.application.port.out.PublishMessagePort;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxMessagePublisherAdapter implements PublishMessagePort {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 발행 성공을 동기로 확인한다. 확인한 뒤에야 호출자가 발행 완료를 표시할 수 있다.
     */
    @Override
    public void publish(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("이벤트 발행 중 인터럽트됐습니다. topic=%s, key=%s".formatted(topic, key), e);
        } catch (Exception e) {
            throw new IllegalStateException("이벤트 발행에 실패했습니다. topic=%s, key=%s".formatted(topic, key), e);
        }
    }
}
