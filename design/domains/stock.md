# 재고 생성과 증가

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
캐시 복구와 대사, 재고 조회 API, 주문 재고 차감은 아직 구현하지 않았다.
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

## 재고 증가 API

`POST /api/stocks/{productId}/increase`는 [스터디 명세](../../docs/api/stock.md)의 재고 증가 응답을 따른다.
현재 구현에서는 HTTP 재전송 방지를 위해 `Idempotency-Key` 헤더를 추가로 요구한다.
키는 공백만으로 구성할 수 없고 최대 128자이며, 재고 증가 API 전체에서 대소문자를 구분해 유일하게 사용한다.
본문의 `quantity`는 필수이며 1~10,000이다. 상품 ID는 양수여야 한다.

외부 코드가 호출하는 공개 진입점은 `StockIncreaseService.increaseForRequest`다.
이 메서드는 트랜잭션을 선언하지 않고 내부 DB 처리 후 Redis 반영을 조율한다.
`StockWriteService`는 `public` 없이 선언해 같은 `com.eda.stock.service` 패키지에서만 접근한다.
내부 `increase` 메서드의 `@Transactional`로 이력·재고·Outbox를 함께 저장한다.
현재 Controller 호출에는 외부 트랜잭션이 없으므로 내부 호출이 커밋을 마친 뒤 Redis를 호출한다.
외부 트랜잭션을 중단하는 설정은 두지 않는다. 향후 다른 트랜잭션 안에서 재사용하면
DB 처리가 해당 트랜잭션에 참여할 수 있으므로 커밋과 Redis 호출 순서를 다시 설계해야 한다.

1. `StockWriteService.increase`의 트랜잭션에서 키와 상품 ID를 이력에 INSERT한다.
2. 이미 저장된 키라면 유일 제약으로 거절한다. 키가 같으면 상품이나 증가량을 비교하지 않는다.
3. 재고를 조회해 증가시키고 `StockIncreased` Outbox를 이력과 함께 커밋한다.
4. 커밋에 성공한 경우에만 `StockIncreaseService`가 Redis의 기존 가용 재고에 증가량을 한 번 더한다.
5. `200 OK`의 `quantity`는 해당 요청이 증가시킨 후의 DB 수량이며, `availableQuantity`는 Redis 증가 명령의 반환값이다.
   Redis 호출에 실패하거나 결과가 불확실하면 `availableQuantity`는 `null`이다.

이력에는 키, 상품 ID, 생성 시각만 남긴다. 요청 증가량, 응답 수량, 응답 완료 상태는 저장하지 않으며
최초 응답을 재반환하지 않는다. 중복 키의 DB 제약 예외는 API 예외 처리에서 `409`로 변환한다.
다른 DB 제약 위반을 중복 키 오류로 일괄 변환하지 않는다.

재고에는 `@Version`을 적용한다. 같은 버전을 동시에 수정하면 한 요청은 실패하고 재시도하지 않는다.
재고 증가와 멱등성 이력 및 Outbox는 한 트랜잭션이므로 낙관적 락 충돌이나 저장 실패 시 함께 롤백한다.
Redis 반영이나 Kafka 발행 실패 때문에 이미 커밋한 재고를 롤백하지 않는다.
Kafka는 기존 Relay가 발행하며 Redis 성공 여부와 발행 순서는 무관하다.
이벤트 필드는 [이벤트 계약](../architecture/events.md)을 따른다.

### 중복 요청과 오류

| 상황 | 응답과 동작 |
|---|---|
| 이미 저장된 키로 재요청 | `409 DUPLICATE_STOCK_INCREASE_REQUEST`, DB와 Redis 및 Outbox 추가 처리 없음 |
| 필수 헤더 누락, 공백 키, 키 길이 초과, 잘못된 상품 ID나 증가량 | `400 VALIDATION_ERROR` |
| 재고 행 없음 | `404 STOCK_NOT_FOUND` |
| 낙관적 락 충돌 | `409 STOCK_INCREASE_CONFLICT`, 자동 재시도 없음 |
| 누적 실재고의 int 범위 초과 | `400 INVALID_STOCK_QUANTITY`, 기존 수량 유지 |

동시에 같은 키가 들어와도 DB 유일 제약으로 한 요청만 커밋된다.
재고나 Outbox 저장이 실패하면 이력도 롤백되므로 같은 키로 다시 요청할 수 있다.
Redis 반영 실패나 DB 커밋 직후 서버 종료가 발생해도 키는 유지되며, 같은 키로 Redis를 다시 실행하지 않는다.

### 이번 구현의 경계

- Redis 반영은 `INCRBY`를 한 번 호출한다. Lettuce는 연결이 끊긴 동안 명령을 거절하고, 재연결 시 `INCRBY`를 재전송하지 않도록 설정한다.
- 키가 없다면 Redis 명령의 동작에 따라 0에서 증가한다. 기존 DB 수량이나 예약을 복원한 값이라고 보장하지 않는다.
- DB 커밋 후 종료되거나 Redis 반영에 실패하면 불일치가 남을 수 있다. 증가 재처리, 대사, 데이터 유실 복구는 구현하지 않는다.
- 최초 성공 응답이 유실되면 재요청은 `409`를 반환할 뿐 원래 응답이나 Redis 반영 결과를 알려주지 않는다.
- 저장된 키를 삭제하거나 새 키로 재요청하면 DB가 다시 증가할 수 있다. 이력 보관·정리 정책과 인증에 따른 키 범위는 후속 과제다.

선택 이유와 위험은 [중복 요청 거절 결정](../adr/0008-stock-increase-duplicate-rejection.md)에 정리했다.
검증은 `StockIncreaseServiceTest`, `StockControllerTest`, PostgreSQL을 사용하는
`StockIncreaseServiceIntegrationTest`, 실제 Valkey를 사용하는 `StockIncreaseApiIntegrationTest`,
Embedded Kafka를 사용하는 `StockIncreaseOutboxRelayIntegrationTest`에서 수행한다.
