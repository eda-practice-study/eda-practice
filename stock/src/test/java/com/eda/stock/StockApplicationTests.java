package com.eda.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.stock.event.StockSnapshot;
import com.eda.stock.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"outbox.enabled=false", "spring.kafka.listener.auto-startup=false"})
class StockApplicationTests {

    @Autowired
    private StockService stocks;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void duplicateInitializationPreservesIncreasedQuantityAndVersion() {
        long productId = 1L;

        stocks.initialize(productId);
        stocks.increase(productId, 5);
        stocks.initialize(productId);

        var initial = new StockSnapshot(productId, 0, 0);
        var updated = new StockSnapshot(productId, 5, 1);
        assertThat(stocks.find(productId)).isEqualTo(updated);
        assertThat(outbox.findByStatusOrderByIdAsc(OutboxStatus.PENDING, Limit.of(3)))
                .extracting(OutboxMessage::getPayload)
                .map(payload -> jsonMapper.readValue(payload, StockSnapshot.class))
                .containsExactly(initial, updated);
    }
}
