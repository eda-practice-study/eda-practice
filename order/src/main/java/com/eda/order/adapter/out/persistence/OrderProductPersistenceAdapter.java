package com.eda.order.adapter.out.persistence;

import com.eda.order.application.port.out.FindOrderProductPort;
import com.eda.order.application.port.out.SaveOrderProductPort;
import com.eda.order.domain.OrderProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderProductPersistenceAdapter implements FindOrderProductPort, SaveOrderProductPort {

    private final OrderProductJpaRepository repository;

    @Override
    public Optional<OrderProduct> findByProductId(Long productId) {
        return repository.findByProductId(productId);
    }

    @Override
    public List<OrderProduct> findAllByProductIdIn(Collection<Long> productIds) {
        return repository.findByProductIdIn(productIds);
    }

    @Override
    public OrderProduct save(OrderProduct orderProduct) {
        return repository.save(orderProduct);
    }
}
