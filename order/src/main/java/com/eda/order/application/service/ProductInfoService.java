package com.eda.order.application.service;

import com.eda.order.application.port.in.CreateProductInfoUseCase;
import com.eda.order.application.port.out.LoadProductInfoPort;
import com.eda.order.application.port.out.SaveProductInfoPort;
import com.eda.order.domain.ProductInfo;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductInfoService implements CreateProductInfoUseCase {

    private final LoadProductInfoPort loadProductInfoPort;
    private final SaveProductInfoPort saveProductInfoPort;

    @Override
    public void createFor(Long productId, String productName, BigDecimal price) {
        if (loadProductInfoPort.findByProductId(productId).isPresent()) {
            return;
        }

        ProductInfo productInfo = ProductInfo.create(productId, productName, price);
        saveProductInfoPort.save(productInfo);
        log.info("주문용 상품 정보 저장 productId={}, name={}, price={}", productId, productName, price);
    }
}
