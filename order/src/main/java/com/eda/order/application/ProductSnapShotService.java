package com.eda.order.application;

import com.eda.common.message.ProductSnapshotRegistered;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.ProductSnapShotRepository;
import com.eda.order.domain.ProductSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductSnapShotService {

    private final ProductSnapShotRepository repository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public void register(
            Long productId,
            String name,
            BigDecimal price
    ) {
        Optional<ProductSnapshot> existingSnapshot =
                repository.findByProductId(productId);

        if (existingSnapshot.isPresent()) {
            return;
        }

        ProductSnapshot snapshot = ProductSnapshot.of(productId, name, price);

        snapshot = repository.save(snapshot);

        outboxWriter.write(
                "order.events",
                String.valueOf(snapshot.getProductId()),
                new ProductSnapshotRegistered(
                        snapshot.getProductId(),
                        snapshot.getName(),
                        snapshot.getPrice().longValueExact()
                )
        );
    }

}
