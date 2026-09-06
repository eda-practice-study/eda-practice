package com.eda.order.adapter.out.persistence;

import com.eda.order.application.port.out.LoadProductInfoPort;
import com.eda.order.application.port.out.SaveProductInfoPort;
import com.eda.order.domain.ProductInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductInfoPersistenceAdapter implements
        SaveProductInfoPort,
        LoadProductInfoPort {

    private final ProductInfoJpaRepository productInfoJpaRepository;

    @Override
    public ProductInfo save(ProductInfo productInfo) {
        return productInfoJpaRepository.save(productInfo);
    }

    @Override
    public List<ProductInfo> loadAllByProductIds(List<Long> productIds) {
        return productInfoJpaRepository.findAllByProductIdIn(productIds);
    }
}
