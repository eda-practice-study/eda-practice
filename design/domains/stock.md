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

`stock:available:{상품ID}`에 Hash 필드 `quantity=0`, `version=0`을 키가 없을 때만 저장한다.
기존 Hash와 문자열 키는 초기화로 덮어쓰지 않으며 TTL은 설정하지 않는다.
실패해도 DB 커밋은 유지하고, 누락이나 불일치는 주기 대사가 복구한다.
재고 조회 API와 주문 재고 차감은 아직 구현하지 않았다.

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
키는 공백만으로 구성할 수 없고 최대 128자다. stock 공통 `request_history`의 `(request_type, idempotency_key)` 복합 PK로 중복을 막는다.
본문의 `quantity`는 필수이며 1~10,000이다. 상품 ID는 양수여야 한다.

공개 진입점 `StockIncreaseService.increaseForRequest`는 DB 커밋 후 수량과 버전을 Redis에 반영한다.
요청 이력·재고·Outbox는 내부 `StockWriteService`의 한 트랜잭션으로 저장한다.
현재 HTTP 호출에는 외부 트랜잭션이 없다. 다른 트랜잭션 안에서 재사용할 경우에는 커밋 경계를 다시 논의한다.

Redis는 증가량을 더하지 않고 DB의 전체 수량을 저장한다. 더 오래된 버전은 무시하고,
같거나 최신인 버전은 수량과 함께 원자적으로 저장한다. API의 `quantity`는 해당 요청의 DB 결과,
`availableQuantity`는 스크립트 처리 후 Redis 수량이며 반영 실패 시 `null`이다.
따라서 더 최신 요청이 먼저 반영되면 두 응답 수량은 다를 수 있다.

이력에는 요청 종류, 키, 생성 시각만 남긴다. 상품 ID나 응답은 저장하지 않으며
최초 응답을 재반환하지 않는다. `request_history_pkey` 위반은 기존 API 예외 처리에서 `409`로 변환한다.
다른 DB 제약 위반을 중복 키 오류로 일괄 변환하지 않는다.

재고에는 `@Version`을 적용한다. 같은 버전을 동시에 수정하면 한 요청은 실패하고 재시도하지 않는다.
재고 증가와 멱등성 이력 및 Outbox는 한 트랜잭션이므로 낙관적 락 충돌이나 저장 실패 시 함께 롤백한다.
Redis 반영이나 Kafka 발행 실패 때문에 이미 커밋한 재고를 롤백하지 않는다.
Kafka는 기존 Relay가 발행하며 Redis 성공 여부와 발행 순서는 무관하다.
이벤트 필드는 [이벤트 계약](../architecture/events.md)을 따른다.

### 중복 요청과 오류

| 상황 | 응답과 동작 |
|---|---|
| 같은 요청 종류와 키로 재요청 | `409 DUPLICATE_STOCK_INCREASE_REQUEST`, DB와 Redis 및 Outbox 추가 처리 없음 |
| 필수 헤더 누락, 공백 키, 키 길이 초과, 잘못된 상품 ID나 증가량 | `400 VALIDATION_ERROR` |
| 재고 행 없음 | `404 STOCK_NOT_FOUND` |
| 낙관적 락 충돌 | `409 STOCK_INCREASE_CONFLICT`, 자동 재시도 없음 |
| 누적 실재고의 int 범위 초과 | `400 INVALID_STOCK_QUANTITY`, 기존 수량 유지 |

동시에 같은 요청 종류와 키가 들어와도 복합 PK로 한 요청만 커밋된다.
재고나 Outbox 저장이 실패하면 이력도 롤백되므로 같은 키로 다시 요청할 수 있다.
Redis 반영 실패나 DB 커밋 직후 서버 종료가 발생해도 키는 유지되며, 같은 키로 Redis를 다시 실행하지 않는다.

### 대사와 복구

기본적으로 시작 시와 대사 완료 60초 후마다 DB 재고를 상품 ID 순서로 100건씩 읽어 Redis에 반영한다.
증가 요청과 같은 버전 비교를 사용하므로 읽기와 쓰기 사이에 새 증가가 발생해도 최신 값을 덮어쓰지 않는다.
실패한 상품은 로그를 남기고 다음 상품을 진행하며, 다음 주기에 다시 시도한다.
대사는 DB와 요청 이력 및 Outbox를 변경하지 않는다.

기존 문자열 키와 누락된 키도 DB 기준으로 복구한다. DB 커밋과 Redis 반영 사이에는 불일치가 남을 수 있다.
재고 감소·예약은 고려하지 않은 정책이며, 향후 도입 시 가용 재고의 복구 기준을 재설계해야 한다.
키 만료, DB 복원으로 버전이 되돌아가는 상황, 멱등성 이력 정리 정책은 이번 범위에 포함하지 않는다.
설정과 배포 절차는 [runbook](../runbook.md#재고-증가-확인), 선택 이유는 [버전 반영과 대사 결정](../adr/0010-stock-cache-reconciliation.md)을 참조한다.
