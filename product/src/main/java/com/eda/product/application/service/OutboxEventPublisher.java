package com.eda.product.application.service;

import com.eda.common.event.EventTopics;
import com.eda.product.application.port.out.OutboxEventPort;
import com.eda.product.application.port.out.PublishMessagePort;
import com.eda.product.domain.outbox.OutboxEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 미발행 outbox 이벤트를 주기적으로 Kafka 로 내보낸다.
 * <p>
 * 트랜잭션을 걸지 않는다. 발행이 끝난 건마다 개별 커밋하므로 Kafka I/O 가 DB 커넥션을 잡지 않고,
 * 도중에 실패해도 이미 발행한 건이 되돌아가 다시 나가는 일이 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventPort outboxEventPort;
    private final PublishMessagePort publishMessagePort;

    @Scheduled(
            fixedDelayString = "${outbox.relay.fixed-delay:1000}",
            initialDelayString = "${outbox.relay.initial-delay:0}")
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxEventPort.findPending()) {
            if (!publish(event)) {
                return;
            }
        }
    }

    /**
     * @return 발행에 성공했는지. 실패하면 뒤 이벤트를 건드리지 않고 멈춰 발행 순서를 지킨다.
     */
    private boolean publish(OutboxEvent event) {
        try {
            publishMessagePort.publish(EventTopics.PRODUCT_EVENTS, event.getAggregateId(), event.getPayload());
        } catch (Exception e) {
            log.error("이벤트 발행에 실패해 이번 주기를 중단합니다. 다음 주기에 재시도합니다. outboxEventId={}, aggregateId={}",
                    event.getId(), event.getAggregateId(), e);
            return false;
        }

        event.markPublished(LocalDateTime.now());
        outboxEventPort.save(event);
        log.info("이벤트를 발행했습니다. outboxEventId={}, aggregateId={}", event.getId(), event.getAggregateId());
        return true;
    }
}
