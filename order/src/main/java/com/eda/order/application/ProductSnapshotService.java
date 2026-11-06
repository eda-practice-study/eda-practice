package com.eda.order.application;

import com.eda.common.message.ProductCreated;
import com.eda.common.message.ProductSnapshotCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.product.ProductSnapshot;
import com.eda.order.domain.product.ProductSnapshotRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductSnapshotService {

    private final ProductSnapshotRepository productSnapshotRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public void create(Long productId, String name, BigDecimal price) {

        // 중복 발행 방지
        if (productSnapshotRepository.existsByProductId(productId)) {
            return;
        }

        // 상품 스냅샷 등록
        ProductSnapshot productSnapshot = ProductSnapshot.of(productId, name, price);
        productSnapshotRepository.save(productSnapshot);

        // 이벤트 발행
        outboxWriter.write(
                "order.events",
                productSnapshot.getId().toString(),
                new ProductSnapshotCreated(
                        productSnapshot.getId(),
                        productSnapshot.getProductId(),
                        productSnapshot.getName(),
                        productSnapshot.getPrice()
                ));
    }
}
