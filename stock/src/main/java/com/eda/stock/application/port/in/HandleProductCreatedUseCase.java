package com.eda.stock.application.port.in;


// CreateStockUseCase와 분리하는 이유
// 걔는 Stock을 생성하는 비지니스 기능이고, 이건 Kafka 이벤트 중복을 검사하고, 생성기능을 호출하는 비지니스 기능이라서
public interface HandleProductCreatedUseCase {

    void handle(HandleCreateProductCommand command);
}
