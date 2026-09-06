package com.eda.order.application.port.out;

import com.eda.order.domain.ProductInfo;
import java.util.Optional;

public interface LoadProductInfoPort {

    Optional<ProductInfo> findByProductId(Long productId);
}
