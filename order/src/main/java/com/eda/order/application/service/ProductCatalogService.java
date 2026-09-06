package com.eda.order.application.service;

import com.eda.order.application.port.in.SynchronizeProductCatalogUseCase;
import com.eda.order.application.port.in.command.SynchronizeProductCatalogCommand;
import com.eda.order.application.port.out.SaveProductCatalogPort;
import com.eda.order.domain.catalog.ProductCatalogItem;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductCatalogService
        implements SynchronizeProductCatalogUseCase {

    private final SaveProductCatalogPort saveProductCatalogPort;

    @Override
    @Transactional
    public void synchronize(SynchronizeProductCatalogCommand command) {
        ProductCatalogItem item = ProductCatalogItem.create(
                command.productId(),
                command.productName(),
                command.unitPrice()
        );

        saveProductCatalogPort.save(item);
    }
}
