package com.eda.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {

    ACTIVE("판매중", "판매 중인 상품"),
    DEACTIVATED("판매중지", "판매 중지된 상품");

    private final String displayName;
    private final String description;
}
