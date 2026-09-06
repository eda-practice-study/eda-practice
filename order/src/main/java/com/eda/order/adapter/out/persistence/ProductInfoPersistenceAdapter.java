package com.eda.order.adapter.out.persistence;

import com.eda.order.application.port.out.LoadProductInfoPort;
import com.eda.order.application.port.out.SaveProductInfoPort;
import com.eda.order.domain.ProductInfo;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ProductInfoPersistenceAdapter implements LoadProductInfoPort, SaveProductInfoPort {

    private final ProductInfoJpaRepository productInfoRepository;

    @Override
    public Optional<ProductInfo> findByProductId(Long productId) {
        return productInfoRepository.findByProductId(productId);
    }

    @Override
    public ProductInfo save(ProductInfo productInfo) {
        return productInfoRepository.save(productInfo);
    }
}
