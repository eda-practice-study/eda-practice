# 초기 재고 생성

## 현재 동작

stock은 `ProductCreated`를 받아 수량이 0인 Stock을 만든다.
상품 ID는 stock 스키마 안에서 유일하며, product 테이블과 DB 외래 키로 연결하지 않는다.
수신 그룹과 이벤트 계약은 [공통 이벤트 문서](../architecture/events.md)를 참조한다.

1. `ProductEventListener`가 타입과 본문 및 헤더를 읽는다.
2. `StockService.createFor(messageId, productId)`가 식별자를 검증한다.
3. 같은 메시지를 처리했다면 `false`로 종료한다.
4. 처리 이력을 저장하고, 이미 Stock이 있는 상품이면 `false`로 종료한다.
5. 새 Stock과 `StockCreated` Outbox를 저장하고 함께 커밋한다.
6. 서비스가 `true`를 반환하면 리스너가 Redis 초기화를 시도한다.
7. 별도 실행 흐름의 Relay가 커밋된 이벤트를 발행한다.

## 중복과 실패

| 상황 | DB 처리 | Redis 처리 |
|---|---|---|
| 신규 메시지와 신규 상품 | 처리 이력과 Stock 및 Outbox 저장 | 커밋 후 초기화 시도 |
| 이미 처리한 메시지 | 추가 저장 없음 | 호출하지 않음 |
| 새 메시지로 기존 상품 수신 | 새 이력만 저장, 기존 수량 보존 | 호출하지 않음 |
| DB 저장 실패 | 전체 롤백, Kafka 재시도 정책 적용 | 호출하지 않음 |
| Redis 접근 실패 | 이미 커밋한 데이터 유지, Kafka는 정상 완료 | 로그만 기록 |

동시 처리 시 이력 PK와 상품 ID 유일 제약으로 중복을 막고, 실패한 요청은 새 트랜잭션에서 재시도한다.
이력은 DB 처리를 완료했다는 뜻이지 Redis 성공을 뜻하지 않는다.

## Redis 초기화

- `infra.StockCache`가 `stock:available:{상품ID}`에 문자열 `"0"`을 `SET NX`로 저장한다. 실제 키 예시는 `stock:available:42`다.
- 기존 키는 덮어쓰지 않고 TTL도 설정하지 않는다. 기존 값이 DB와 일치하는지 보정하지 않는다.
- Redis 접근의 `DataAccessException`은 로그 후 반환한다. 연결과 명령 타임아웃은 각각 500ms다.
- DB 커밋 후 초기화 전 종료되면 키가 누락될 수 있다. 중복 메시지로 복구하지 않는다.
- 재고 변경과 지연된 초기화가 겹칠 때 최신 값이 보장되지는 않는다.

선택한 이유와 예약 수량 복원 위험은 [Redis 실패 허용 결정](../adr/0004-stock-redis-initialization.md)에 있다.
캐시 복구, 재고 증가와 조회 API, 주문 재고 차감은 아직 구현하지 않았다.
[스터디 재고 API 명세](../../docs/api/stock.md)를 이미 구현된 API로 해석하지 않는다.

`StockCreated`는 DB의 초기 재고 생성 사실이다. Redis가 먼저 준비된다는 순서 보장은 없다.
이벤트의 수량 0은 발생 당시 값이며 이후 현재 수량을 조회한 결과가 아니다.

## 코드와 검증

- [서비스](../../stock/src/main/java/com/eda/stock/service/StockService.java)
- [리스너](../../stock/src/main/java/com/eda/stock/listener/ProductEventListener.java)
- [Redis 접근](../../stock/src/main/java/com/eda/stock/infra/StockCache.java)
- [DB 통합 테스트](../../stock/src/test/java/com/eda/stock/service/StockServiceIntegrationTest.java)
- [Kafka와 Redis 통합 테스트](../../stock/src/test/java/com/eda/stock/listener/ProductEventListenerIntegrationTest.java)
- [발행 통합 테스트](../../stock/src/test/java/com/eda/stock/outbox/StockOutboxRelayIntegrationTest.java)
