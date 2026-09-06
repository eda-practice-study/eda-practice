package com.eda.order.application.port.in;

import com.eda.order.application.port.in.command.SynchronizeProductCatalogCommand;

public interface SynchronizeProductCatalogUseCase {

    void synchronize(SynchronizeProductCatalogCommand command);
}
