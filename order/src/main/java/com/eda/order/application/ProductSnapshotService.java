package com.eda.order.application;

import com.eda.common.message.Topics;
import com.eda.common.message.order.ProductSnapshotRegistered;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.infrastructure.ProductSnapshotRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductSnapshotService {

    private final ProductSnapshotRepository productSnapshotRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public void register(Long productId, String name, BigDecimal price) {
        if (productSnapshotRepository.existsByProductId(productId)) {
            return;
        }

        productSnapshotRepository.save(ProductSnapshot.of(productId, name, price));
        outboxWriter.write(Topics.ORDER_EVENTS, String.valueOf(productId),
                new ProductSnapshotRegistered(productId, name, price));
    }
}
