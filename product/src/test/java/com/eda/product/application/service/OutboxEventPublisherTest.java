package com.eda.product.application.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.event.EventTopics;
import com.eda.product.application.port.out.OutboxEventPort;
import com.eda.product.application.port.out.PublishMessagePort;
import com.eda.product.domain.outbox.OutboxEvent;
import com.eda.product.domain.outbox.OutboxStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxEventPublisherTest {

    private FakeOutboxEventStore outboxEventPort;
    private FakeMessagePublisher publishMessagePort;
    private OutboxEventPublisher outboxEventPublisher;

    @BeforeEach
    void setUp() {
        outboxEventPort = new FakeOutboxEventStore();
        publishMessagePort = new FakeMessagePublisher();
        outboxEventPublisher = new OutboxEventPublisher(outboxEventPort, publishMessagePort);
    }

    @Test
    @DisplayName("미발행 이벤트를 발행하고 PUBLISHED로 표시한다")
    void publishPendingEvent() {
        // given
        OutboxEvent event = outboxEventPort.store(pendingEvent(1L, "1"));

        // when
        outboxEventPublisher.publishPendingEvents();

        // then
        assertThat(publishMessagePort.sent).hasSize(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("토픽과 메시지 키, payload를 그대로 실어 보낸다")
    void sendPayloadAsIs() {
        // given
        outboxEventPort.store(pendingEvent(1L, "42"));

        // when
        outboxEventPublisher.publishPendingEvents();

        // then: 릴레이는 payload 를 해석하지 않는다
        assertThat(publishMessagePort.sent.getFirst())
                .isEqualTo(new SentMessage(EventTopics.PRODUCT_EVENTS, "42", "{\"productId\":42}"));
    }

    @Test
    @DisplayName("미발행 이벤트가 여러 건이면 오래된 순으로 모두 발행한다")
    void publishAllPendingEvents() {
        // given
        outboxEventPort.store(pendingEvent(1L, "1"));
        outboxEventPort.store(pendingEvent(2L, "2"));
        outboxEventPort.store(pendingEvent(3L, "3"));

        // when
        outboxEventPublisher.publishPendingEvents();

        // then
        assertThat(publishMessagePort.sent).extracting(SentMessage::key).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("발행에 실패하면 그 건부터 멈춰 뒤 이벤트의 순서를 지킨다")
    void stopOnFailureToKeepOrder() {
        // given: 두 번째 이벤트 발행이 실패하는 상황
        OutboxEvent first = outboxEventPort.store(pendingEvent(1L, "1"));
        OutboxEvent second = outboxEventPort.store(pendingEvent(2L, "2"));
        OutboxEvent third = outboxEventPort.store(pendingEvent(3L, "3"));
        publishMessagePort.failOnKey = "2";

        // when
        outboxEventPublisher.publishPendingEvents();

        // then: 세 번째는 시도조차 하지 않는다
        assertThat(publishMessagePort.sent).extracting(SentMessage::key).containsExactly("1");
        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(third.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("실패한 이벤트는 다음 주기에 다시 발행된다")
    void retryFailedEventOnNextRun() {
        // given
        OutboxEvent event = outboxEventPort.store(pendingEvent(1L, "1"));
        publishMessagePort.failOnKey = "1";
        outboxEventPublisher.publishPendingEvents();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

        // when: 장애가 풀린 뒤 다음 주기
        publishMessagePort.failOnKey = null;
        outboxEventPublisher.publishPendingEvents();

        // then: 유실되지 않는다
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("미발행 이벤트가 없으면 아무것도 발행하지 않는다")
    void doNothingWithoutPendingEvent() {
        // when
        outboxEventPublisher.publishPendingEvents();

        // then
        assertThat(publishMessagePort.sent).isEmpty();
    }

    @Test
    @DisplayName("이미 발행된 이벤트는 다시 발행하지 않는다")
    void doNotPublishTwice() {
        // given
        outboxEventPort.store(pendingEvent(1L, "1"));
        outboxEventPublisher.publishPendingEvents();

        // when
        outboxEventPublisher.publishPendingEvents();

        // then
        assertThat(publishMessagePort.sent).hasSize(1);
    }

    private OutboxEvent pendingEvent(Long id, String aggregateId) {
        OutboxEvent event = OutboxEvent.pending(
                "PRODUCT", aggregateId, "PRODUCT_CREATED", "{\"productId\":" + aggregateId + "}");
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    /**
     * JPA 없이 outbox 저장소를 흉내낸다. 미발행 필터와 id 정렬까지 실제와 동일하게 맞춘다.
     */
    private static class FakeOutboxEventStore implements OutboxEventPort {

        private final List<OutboxEvent> store = new ArrayList<>();

        OutboxEvent store(OutboxEvent event) {
            store.add(event);
            return event;
        }

        @Override
        public OutboxEvent save(OutboxEvent outboxEvent) {
            return outboxEvent;
        }

        @Override
        public List<OutboxEvent> findPending() {
            return store.stream()
                    .filter(event -> event.getStatus() == OutboxStatus.PENDING)
                    .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                    .toList();
        }
    }

    /**
     * 보낸 메시지를 모아두는 가짜 발행기. failOnKey 로 특정 메시지의 발행 실패를 재현한다.
     */
    private static class FakeMessagePublisher implements PublishMessagePort {

        private final List<SentMessage> sent = new ArrayList<>();
        private String failOnKey;

        @Override
        public void publish(String topic, String key, String payload) {
            if (key.equals(failOnKey)) {
                throw new IllegalStateException("발행 실패");
            }
            sent.add(new SentMessage(topic, key, payload));
        }
    }

    private record SentMessage(String topic, String key, String payload) {
    }
}
