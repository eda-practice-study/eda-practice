# 주문용 상품 스냅샷 생성

## 현재 동작

order는 `ProductCreated`의 상품 ID와 이름 및 가격을 복사해 `ProductSnapshot`을 만든다.
스냅샷은 `orders.product_snapshot`에 저장하며 상품 ID는 유일하다.
이 기능은 stock의 이벤트를 기다리지 않고 독립적으로 실행한다.

1. `ProductEventListener`가 상품 생성 이벤트를 변환한다.
2. `ProductSnapshotService.createFor(messageId, event)`가 필수값을 검증한다.
3. 같은 메시지를 처리했다면 `false`로 종료한다.
4. 처리 이력을 저장하고, 해당 상품의 스냅샷이 이미 있으면 `false`로 종료한다.
5. 새 스냅샷과 `ProductSnapshotCreated` Outbox를 저장한다.
6. 처리 이력과 스냅샷 및 Outbox를 함께 커밋하고 `true`를 반환한다.
7. order 내부 Relay가 [계약에 정의된 생성 이벤트](../architecture/events.md)를 발행한다.

본문이나 messageId가 없거나 상품 ID와 가격이 양수가 아니거나 상품명이 null 또는 공백이면 형식 오류다.
엔티티는 가격을 `BigDecimal`로 저장하고 이벤트는 원 단위 정수 `long`으로 전달한다.

## 기존 값 보존

같은 messageId는 추가 저장 없이 건너뛴다.
다른 messageId라도 같은 상품의 스냅샷이 있으면 새 처리 이력만 기록한다.
상품 이름과 가격이 다르게 수신되어도 기존 값을 덮어쓰거나 새 생성 이벤트를 만들지 않는다.
이 선택의 근거는 [스냅샷 생성 전용 결정](../adr/0005-snapshot-create-only.md)에 있다.

상품 수정 이벤트 처리와 갱신 순서는 아직 설계하지 않았다.
주문 엔티티의 일부 동작과 `toLineItem()`은 존재하지만 주문 API와 Saga는 구현하지 않았다.

## 실패와 이벤트 의미

- 스냅샷이나 Outbox 저장 실패는 처리 이력까지 함께 롤백한다.
- 동시 중복은 이력 PK와 상품 ID 유일 제약으로 보호하며 Kafka 재시도 시 새 트랜잭션에서 확인한다.
- [공통 ACK와 실패 정책](../architecture/events.md)에 따라 재시도하거나 order 전용 DLT로 보낸다.
- `ProductSnapshotCreated`는 order DB에 스냅샷이 생겼다는 뜻이다. stock이나 Redis의 준비 완료를 보장하지 않는다.
- Kafka 발행이 지연될 수 있고 Relay 중복 발행도 가능하다.

## 코드와 검증

- [스냅샷 엔티티](../../order/src/main/java/com/eda/order/domain/ProductSnapshot.java)
- [서비스](../../order/src/main/java/com/eda/order/service/ProductSnapshotService.java)
- [리스너](../../order/src/main/java/com/eda/order/listener/ProductEventListener.java)
- [서비스 단위 테스트](../../order/src/test/java/com/eda/order/service/ProductSnapshotServiceTest.java)
- [DB 통합 테스트](../../order/src/test/java/com/eda/order/service/ProductSnapshotServiceIntegrationTest.java)
- [수신과 발행 통합 테스트](../../order/src/test/java/com/eda/order/listener/ProductEventListenerIntegrationTest.java)
