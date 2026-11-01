package com.eda.order.application;

import com.eda.common.message.order.ProductSnapshotRegistered;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.domain.ProductSnapshotRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductSnapshotService {

    private static final String TOPIC = "order.events";

    private final ProductSnapshotRepository productSnapshotRepository;
    private final OutboxWriter outboxWriter;

    // 같은 상품 생성 이벤트가 다시 와도 스냅샷과 이벤트는 한 번만 만든다
    @Transactional
    public void registerIfAbsent(ProductCreated product) {
        if (productSnapshotRepository.existsByProductId(product.productId())) {
            return;
        }

        productSnapshotRepository.save(
                ProductSnapshot.of(product.productId(), product.name(), BigDecimal.valueOf(product.price())));
        outboxWriter.write(TOPIC, String.valueOf(product.productId()),
                new ProductSnapshotRegistered(product.productId(), product.name(), product.price()));
    }
}
