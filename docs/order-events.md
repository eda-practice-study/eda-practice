# order 서버의 상품 스냅샷 생성

## 이벤트 계약

| 구분 | 토픽 | 그룹 | 메시지 타입 | key |
|---|---|---|---|---|
| 수신 | `product.events` | `order-service` | `ProductCreated` | 상품 ID 문자열 |
| 발행 | `order.events` | 소비자가 지정 | `ProductSnapshotCreated` | 상품 ID 문자열 |
| 수신 실패 보관 | `order.product.events.DLT` | 후속 처리에서 지정 | 원본 타입 보존 | 원본 key 보존 |

`ProductSnapshotCreated`는 common의 `message.order` 패키지에 정의한다.
본문에는 생성된 스냅샷의 상품 ID·상품명·원 단위 정수 가격을 담는다.

```json
{"productId":1,"name":"키보드","price":30000}
```

`messageId`, `messageType`, `occurredAt`은 Kafka 헤더로 전달한다.
입력 메시지와 발행 메시지의 messageId는 서로 다르며, Relay가 재발행할 때는 Outbox에 저장한 UUID를 재사용한다.

## 처리 순서와 트랜잭션

```text
ProductEventListener
  → messageType 확인: ProductCreated만 처리
  → Message.from()으로 헤더와 본문 변환
  → ProductSnapshotService.createFor(messageId, event)
      → 식별자·상품명·가격 검증
      → 처리한 messageId면 종료
      → 처리 이력 저장
      → 기존 상품 스냅샷이면 종료
      → ProductSnapshot 저장
      → ProductSnapshotCreated를 Outbox에 저장
      → DB 커밋
  → 리스너 반환
  → BATCH ACK 정책으로 오프셋 커밋

별도 OutboxRelay
  → 커밋된 PENDING 이벤트를 order.events로 발행
  → 발행 성공 시 PUBLISHED로 변경
```

- `ProductSnapshotService`는 messageId와 `ProductCreated`를 받는다. 서비스의 boolean 반환값은 신규 생성 여부다.
- messageId·본문 누락, null 또는 양수가 아닌 상품 ID, null·공백 상품명, 양수가 아닌 가격은 형식 오류로 처리한다.
- 엔티티의 가격은 기존 `BigDecimal`을 사용하고 이벤트 가격은 `long`으로 표현한다.
- 처리 이력·스냅샷·Outbox는 `orders` 스키마의 한 DB 트랜잭션에서 저장된다.
  스냅샷 또는 Outbox 저장·커밋 실패 시 모두 롤백한다. 명시적인 flush는 추가하지 않는다.
- order 전용 `processed_message`는 messageId UUID를 PK로 사용한다. 할당된 UUID가 merge로 덮어써지지 않도록
  `Persistable`을 구현하고 신규 이력은 INSERT한다.
- 스냅샷의 productId 유일 제약과 처리 이력 PK로 동시 중복을 보호한다.
  경쟁에서 실패한 트랜잭션은 롤백되고 재시도 시 이미 커밋된 이력이나 스냅샷을 확인한다.

## 중복과 기존 스냅샷 정책

| 수신 상황 | 결과 |
|---|---|
| 신규 메시지·신규 상품 | 이력·스냅샷·생성 이벤트를 함께 저장 |
| 이미 처리한 messageId | 추가 저장 없이 종료 |
| 다른 messageId·기존 상품 | 새 이력만 저장하고 기존 이름·가격 보존, 생성 이벤트 추가 없음 |
| 다른 이벤트 타입 | 본문을 역직렬화하지 않고 건너뜀 |

이름·가격이 달라도 `ProductCreated`로 기존 스냅샷을 갱신하지 않는다.
상품 수정 이벤트를 통한 갱신은 후속 설계 대상이다.

## ACK·재시도·DLT

- `enable-auto-commit=false`, `ack-mode=batch`를 사용한다. 리스너는 메시지를 한 건씩 동기 처리한다.
- DB 트랜잭션은 서비스 프록시가 완료하고, 이후 리스너가 정상 반환한다.
  DB 커밋 후 오프셋 커밋 전에 종료되면 재수신될 수 있으나 처리 이력이 중복 저장을 막는다.
- `auto-offset-reset=earliest`는 그룹에 유효한 커밋 위치가 없을 때 적용된다.
- DB·Outbox 저장 실패는 최초 시도 후 1초 간격으로 3회 재시도한 뒤 DLT로 보낸다.
- 메시지 변환 실패와 서비스 입력 검증 실패는 `InvalidProductEventException`으로 전달하고 즉시 DLT로 보낸다.
- DLT는 원본 key·본문·이벤트 헤더와 실패 정보를 보존한다. 목적지 파티션은 producer가 선택한다.
- DLT 발행 결과를 확인하며, 발행 실패 시 원본을 처리 완료하지 않는다. 복구 후 다시 처리한다.
- DLT에 보관된 상품에는 스냅샷이 없을 수 있다. 원인 해결 후 별도 재처리가 필요하다.
  DLT 조회·재처리 API는 이번 구현에 포함하지 않는다.

## 발행 이벤트의 의미와 한계

`ProductSnapshotCreated`는 order DB에 상품 스냅샷이 생성되었다는 사실을 알린다.
stock 서버의 재고·Redis 준비 여부나 다른 소비자의 처리 완료는 보장하지 않는다.
스냅샷 생성과 Kafka 발행은 비동기이며, Kafka 장애 시 Outbox에 남은 메시지를 Relay가 재시도한다.
발행 성공 후 상태 저장 전에 종료되면 중복 발행될 수 있으므로 소비자도 messageId 기반 멱등 처리가 필요하다.

현재 구성은 Hibernate가 테이블을 생성한다. 운영 마이그레이션과 처리 이력·Outbox 정리 정책은 후속 과제다.
Kafka 토픽 자동 생성을 끈 환경에서는 `product.events`, `order.events`, `order.product.events.DLT`를 준비해야 한다.

## 검증

- 서비스·리스너 단위 테스트: 데이터 복사, 타입 구분, 입력 검증, 중복 처리, 기존 값 유지, 실패 전파.
- PostgreSQL 17 Testcontainers: 세 데이터의 원자적 커밋·롤백과 동시 중복 처리.
- Embedded Kafka: 수신부터 OutboxRelay 발행까지의 흐름, key·본문·헤더, 재시도, DLT 이동,
  DLT 발행 실패 시 원본 오프셋 보존 및 복구 후 처리.
- DLT producer의 전송 결과가 실패하면 성공으로 간주하지 않는 단위 테스트.

전체 검증 명령은 `./gradlew :order:test :stock:test :product:test`다. 컨테이너 통합 테스트에는 Docker가 필요하다.
테스트의 검증은 각 테스트에 직접 작성하며 공통 단언 헬퍼로 묶지 않는다.
