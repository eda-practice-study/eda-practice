package com.eda.product.application.port.out;

import com.eda.product.domain.outbox.OutboxEvent;
import java.util.List;

public interface OutboxEventPort {

    OutboxEvent save(OutboxEvent outboxEvent);

    /**
     * 미발행 이벤트를 오래된 순으로 한 묶음 조회한다. 발행 순서를 지키기 위해 id 오름차순이다.
     */
    List<OutboxEvent> findPending();
}
