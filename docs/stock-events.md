# 상품 생성 이벤트 소비

stock 서버의 `ProductEventListener`는 `product.events`를 `stock-service` 그룹으로 구독한다.
`messageType=ProductCreated`인 메시지만 처리하며 다른 이벤트 타입은 건너뛴다.
key와 value는 문자열로 수신하고, 공통 `Message.from()`으로 이벤트 헤더와 JSON 본문을 변환한다.

## 초기 재고와 중복 처리

- `StockService.createFor(messageId, productId)`는 하나의 DB 트랜잭션에서 처리 이력과 재고를 저장한다.
- `processed_message`의 UUID PK로 처리 이력을 식별한다. 처리 완료된 메시지는 건너뛴다.
- 이력을 먼저 INSERT하고 flush한다. 재고 생성에 실패하면 이력도 함께 롤백된다.
- 새 상품의 재고 수량은 0이다. 이미 Stock이 있으면 수량을 유지하고 새 메시지의 이력만 기록한다.
- 동시에 들어온 메시지는 처리 이력 PK와 Stock의 상품 ID 유일 제약으로 보호한다.
  제약 위반이 발생한 요청은 롤백되고 새 트랜잭션에서 재시도된다.
- DB 커밋 후 오프셋 커밋 전 종료되면 재수신될 수 있지만, 처리 이력을 통해 중복 변경을 막는다.

## ACK와 실패 처리

- `enable-auto-commit=false`, `ack-mode=batch`를 사용한다. 리스너는 한 건씩 동기 처리한다.
- `auto-offset-reset=earliest`는 그룹에 유효한 오프셋이 없을 때만 적용된다.
- 저장 실패는 최초 시도 후 1초 간격으로 3회 재시도한다.
- 잘못된 JSON, 필수 이벤트 헤더 누락, 잘못된 식별자는 즉시 DLT로 보낸다.
- DLT는 `stock.product.events.DLT`이며, 실패 원인과 원본 메시지의 key, value, 이벤트 헤더를 보존한다.
  DLT의 파티션은 producer가 선택하므로 원본 토픽과 파티션 수가 같을 필요는 없다.
- DLT 전송 결과가 성공한 뒤 원본 메시지를 처리 완료한다. DLT 발행 실패는 전파해 원본을 재처리한다.
- DLT 발행 성공 후 오프셋 커밋 전 종료되면 DLT도 중복 발행될 수 있다.
- DLT에 저장된 이벤트의 Stock은 생성되지 않는다. 원인 해결 후 별도 재처리가 필요하며,
  DLT 조회·재처리 API와 처리 이력 정리 정책은 후속 구현 대상이다.

개발 환경은 Kafka 토픽 자동 생성을 사용한다. 자동 생성을 끈 환경에서는 두 토픽을 사전에 생성해야 한다.
처리 이력 테이블은 현재 프로젝트의 Hibernate 스키마 생성 정책을 따른다.

## 검증

`./gradlew :stock:test :product:test`로 검증한다.
stock의 PostgreSQL 17 Testcontainers 테스트에는 실행 중인 Docker가 필요하며, Docker가 없을 때 자동으로 건너뛰지 않는다.
Kafka 통합 테스트는 Embedded Kafka를 사용한다.
