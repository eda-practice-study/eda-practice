package com.eda.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.eda.common.message.MessageHeader;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.order.domain.ProductSnapshotRepository;
import com.eda.order.event.ProductCreatedListener;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;

@SpringBootTest(properties = {"outbox.enabled=false", "spring.kafka.listener.auto-startup=false"})
class OrderApplicationTests {

    @Autowired
    private ProductCreatedListener listener;

    @Autowired
    private ProductSnapshotRepository snapshots;

    @Autowired
    private OutboxRepository outbox;

    @Test
    void redeliveryKeepsOneSnapshotAndOneRegistrationEvent() {
        var record = new ConsumerRecord<>("product.events", 0, 0L, "1",
                """
                {"productId":1,"name":"키보드","price":30000}
                """);
        record.headers().add(MessageHeader.MESSAGE_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.MESSAGE_TYPE, "ProductCreated".getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.OCCURRED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));

        listener.register(record);
        listener.register(record);

        assertThat(snapshots.findAll()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getProductId()).isEqualTo(1L);
            assertThat(snapshot.getName()).isEqualTo("키보드");
            assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
        });
        assertThat(outbox.findByStatusOrderByIdAsc(OutboxStatus.PENDING, Limit.of(2)))
                .singleElement().satisfies(event -> {
                    assertThat(event.getTopic()).isEqualTo("order.events");
                    assertThat(event.getMessageType()).isEqualTo("ProductSnapshotRegistered");
                });
    }
}
