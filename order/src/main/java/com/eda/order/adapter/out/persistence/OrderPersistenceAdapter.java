package com.eda.order.adapter.out.persistence;

import com.eda.order.application.port.out.FindOrderPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements FindOrderPort, SaveOrderPort {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Optional<Order> findById(Long orderId) {
        return orderJpaRepository.findById(orderId);
    }

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }
}
