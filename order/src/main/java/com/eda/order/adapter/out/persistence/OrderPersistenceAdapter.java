package com.eda.order.adapter.out.persistence;

import com.eda.order.application.port.out.LoadOrderPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class OrderPersistenceAdapter implements LoadOrderPort, SaveOrderPort {

    private final OrderJpaRepository orderRepository;

    @Override
    public Optional<Order> findById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    @Override
    public Order save(Order order) {
        return orderRepository.save(order);
    }
}
