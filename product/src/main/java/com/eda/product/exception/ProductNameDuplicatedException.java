package com.eda.product.exception;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;

public class ProductNameDuplicatedException extends BusinessException {

    public ProductNameDuplicatedException() {
        super(ErrorCode.PRODUCT_NAME_DUPLICATED);
    }
}
