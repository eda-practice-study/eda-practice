# 상품 생성 이벤트 소비

stock 서버의 `ProductEventListener`는 `product.events`를 `stock-service` 그룹으로 구독한다.
`messageType=ProductCreated`인 메시지만 처리하며 다른 이벤트 타입은 건너뛴다.
key와 value는 문자열로 수신하고, 공통 `Message.from()`으로 이벤트 헤더와 JSON 본문을 변환한다.

## 초기 재고와 중복 처리

- `StockService.createFor(messageId, productId)`는 하나의 DB 트랜잭션에서 처리 이력과 재고 및 생성 이벤트의 Outbox를 저장한다.
- `processed_message`의 UUID PK로 처리 이력을 식별한다. 처리 완료된 메시지는 건너뛴다.
- 이력을 먼저 저장하고, 실제 INSERT는 JPA의 flush 시점에 실행한다. 재고 생성이나 Outbox 저장에 실패하면 모두 함께 롤백된다.
- 새 상품의 재고 수량은 0이다. 이미 Stock이 있으면 수량을 유지하고 새 메시지의 이력만 기록한다.
- 동시에 들어온 메시지는 처리 이력 PK와 Stock의 상품 ID 유일 제약으로 보호한다.
  제약 위반이 발생한 요청은 롤백되고 새 트랜잭션에서 재시도된다.
- DB 커밋 후 오프셋 커밋 전 종료되면 재수신될 수 있지만, 처리 이력을 통해 중복 변경을 막는다.

## 재고 생성 이벤트 발행

- 신규 Stock을 생성할 때 `OutboxWriter`로 `StockCreated(Long productId, int quantity)`를 기록한다.
  토픽은 `stock.events`, Kafka key는 상품 ID 문자열이며 초기 quantity는 0이다.
- 서비스 트랜잭션 안에서 Stock을 저장한 다음 Outbox를 저장한다. 처리 이력·Stock·Outbox가 함께 커밋되거나 롤백된다.
- 같은 메시지를 재수신하거나 다른 메시지 ID로 기존 상품을 수신하면 새 Outbox를 만들지 않는다.
- Outbox에는 입력 `ProductCreated`와 별개의 새 messageId가 부여된다. Relay가 재발행할 때는 저장된 ID를 재사용한다.
- stock 서버의 기존 OutboxRelay가 Kafka로 발행한다. Kafka 발행에 실패하면 PENDING 상태로 남아 다음 회차에 재시도한다.
- 이벤트는 **DB에 초기 재고가 생성되었다는 사실**을 의미한다. Redis 반영 완료나 이벤트 수신 시점의 최신 수량을 의미하지 않는다.
  Redis 반영 실패에도 이벤트는 발행되며, Relay는 Redis 초기화보다 먼저 발행할 수 있다.
- Relay 재발행으로 Kafka 중복 전달이 가능하므로 소비자는 messageId 기반 멱등 처리가 필요하다.

## Redis 초기 재고

- 서비스는 신규 Stock을 만들었으면 `true`, 중복 메시지나 기존 Stock이면 `false`를 반환한다.
- 리스너는 신규 Stock의 DB 커밋 이후에만 Redis에 수량 0을 `SET NX`로 추가한다.
- Redis 실패는 로그로 남기고 DB 커밋과 Kafka 처리 성공을 유지한다. Redis 실패로 Kafka 재시도나 DLT를 발생시키지 않는다.
- Redis 초기화 누락은 중복 이벤트로 복구하지 않는다. 조회·재고 변경 시의 복구 정책은 후속 과제다.
- [핵심 결정과 감수한 위험](stock-redis-initialization.md)에 커밋 경계, 타임아웃, 유실 구간과 향후 복구 제약을 정리했다.

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

개발 환경은 Kafka 토픽 자동 생성을 사용한다. 자동 생성을 끈 환경에서는 `product.events`, `stock.events`, `stock.product.events.DLT`를 사전에 생성해야 한다.
처리 이력 테이블은 현재 프로젝트의 Hibernate 스키마 생성 정책을 따른다.

## 검증

`./gradlew :stock:test :product:test`로 검증한다.
stock의 PostgreSQL 17 Testcontainers 테스트에는 실행 중인 Docker가 필요하며, Docker가 없을 때 자동으로 건너뛰지 않는다.
Kafka 통합 테스트는 Embedded Kafka와 실제 Valkey 컨테이너를 사용한다.
