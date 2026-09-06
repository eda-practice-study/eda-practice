package com.eda.order.adapter.out.persistence;

import com.eda.order.application.port.out.LoadOrderPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements
        SaveOrderPort,
        LoadOrderPort {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> loadById(Long orderId) {
        return orderJpaRepository.findById(orderId);
    }
}
