package com.eda.product.adapter.out.persistence.outbox;

import static org.assertj.core.api.Assertions.*;

import com.eda.product.domain.outbox.OutboxEvent;
import com.eda.product.domain.outbox.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(OutboxEventPersistenceAdapter.class)
class OutboxEventPersistenceAdapterTest {

    @Autowired
    private OutboxEventPersistenceAdapter outboxEventPersistenceAdapter;

    @Test
    @DisplayName("이벤트를 저장하면 ID와 생성 시각이 채워진다")
    void saveOutboxEvent() {
        // when
        OutboxEvent saved = outboxEventPersistenceAdapter.save(pendingEvent("1"));

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("미발행 이벤트만 오래된 순으로 조회한다")
    void findPendingInIdOrder() {
        // given
        OutboxEvent published = outboxEventPersistenceAdapter.save(pendingEvent("1"));
        published.markPublished(LocalDateTime.now());
        outboxEventPersistenceAdapter.save(published);

        OutboxEvent second = outboxEventPersistenceAdapter.save(pendingEvent("2"));
        OutboxEvent third = outboxEventPersistenceAdapter.save(pendingEvent("3"));

        // when
        List<OutboxEvent> pending = outboxEventPersistenceAdapter.findPending();

        // then: 발행된 건은 빠지고, 남은 건은 id 오름차순이다
        assertThat(pending).extracting(OutboxEvent::getId)
                .containsExactly(second.getId(), third.getId());
    }

    @Test
    @DisplayName("미발행 이벤트가 없으면 빈 목록을 반환한다")
    void findPendingReturnsEmpty() {
        // when & then
        assertThat(outboxEventPersistenceAdapter.findPending()).isEmpty();
    }

    @Test
    @DisplayName("한 번에 최대 100건까지만 조회한다")
    void findPendingUpTo100() {
        // given
        for (int i = 0; i < 101; i++) {
            outboxEventPersistenceAdapter.save(pendingEvent(String.valueOf(i)));
        }

        // when & then
        assertThat(outboxEventPersistenceAdapter.findPending()).hasSize(100);
    }

    private OutboxEvent pendingEvent(String aggregateId) {
        return OutboxEvent.pending("PRODUCT", aggregateId, "PRODUCT_CREATED", "{\"productId\":" + aggregateId + "}");
    }
}
