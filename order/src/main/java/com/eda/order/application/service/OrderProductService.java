package com.eda.order.application.service;

import com.eda.order.application.port.in.CreateOrderProductCommand;
import com.eda.order.application.port.in.CreateOrderProductUseCase;
import com.eda.order.application.port.out.FindOrderProductPort;
import com.eda.order.application.port.out.SaveOrderProductPort;
import com.eda.order.domain.OrderProduct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderProductService implements CreateOrderProductUseCase {

    private final FindOrderProductPort findOrderProductPort;
    private final SaveOrderProductPort saveOrderProductPort;

    @Override
    public void create(CreateOrderProductCommand command) {
        if(findOrderProductPort.findByProductId(command.productId()).isPresent()) {
            return;
        }

        OrderProduct orderProduct = OrderProduct.create(command.productId(), command.name(), command.price());
        saveOrderProductPort.save(orderProduct);
    }
}
