# 2주차 구현 — Transactional Outbox

## 범위

| 구분 | 내용 |
|------|------|
| 해결 | 1주차 `A-1. 이벤트 유실` — DB 저장과 이벤트 발행의 원자성 부재 |
| 추가 | `product.outbox_event` 테이블, 폴링 릴레이 |
| 변경 | `PublishProductEventPort` 구현을 Kafka 직접 발행 → outbox 기록으로 교체 |
| 무변경 | `ProductCommandService`, `AdminProductController`, stock 전체 |

---

## 무엇이 문제였나

1주차는 커밋이 확정된 뒤(`afterCommit`) Kafka로 보냈다. 유령 이벤트는 막았지만 반대쪽이 열려 있었다.

```
┌─ TX ──────────────┐
│ INSERT product    │
└─ COMMIT ──────────┘   ← 여기까지 성공
      │
      ▼
   send()  ✗ 실패     ← 이벤트가 사라진다
```

`send()` 는 비동기라 API는 이미 201을 반환한 뒤다. 남는 것은 ERROR 로그뿐이고, 상품은 있는데 재고 row가 영영 생기지 않아 재고 등록이 계속 404가 된다.

**원인은 DB와 Kafka가 서로 다른 저장소라는 것**이다. 둘을 한 트랜잭션으로 묶을 수 없으니, 이벤트를 DB 안으로 끌고 들어와 상품과 같은 트랜잭션에 넣는다. 그것이 outbox다.

---

## 흐름

### ① 저장 — `POST /admin/products`

```
Admin          product:8083
  │ POST /admin/products
  ├──────────────►│
  │               │ ┌─ TX ─────────────────────────┐
  │               │ │ ① INSERT product             │
  │               │ │ ② INSERT outbox_event(PENDING)│
  │               │ └─ COMMIT ────────────────────┘
  │ 201 {productId}
  │◄──────────────┤        Kafka 호출 없음
```

①과 ②는 같은 트랜잭션이다. **롤백되면 둘 다 사라지고, 커밋되면 둘 다 남는다.** Kafka가 죽어 있어도 상품 생성은 성공하고 이벤트는 `PENDING` 으로 보존된다.

### ② 발행 — 1초마다

```
product:8083                                Kafka
  │ @Scheduled(fixedDelay=1000)
  │ SELECT ... WHERE status='PENDING'
  │   ORDER BY id LIMIT 100
  │
  │ for each:
  │   send(key=aggregate_id, value=payload).get(10s)
  ├───────────────────────────────────────────►│
  │   성공 → UPDATE status='PUBLISHED'          │   ← 건당 개별 커밋
  │   실패 → 중단, 다음 주기에 재시도              │
```

### ③ 소비 — stock (1주차 그대로)

```
stock:8084
  │ @KafkaListener(product.events)
  │ existsByProductId? ─ 예 ─► skip
  │                    └ 아니오 ─► INSERT stock(qty=0)
```

---

## 테이블 — `product.outbox_event`

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | bigserial | PK. **발행 순서 = id 순서** |
| `aggregate_type` | varchar(50) | `PRODUCT` |
| `aggregate_id` | varchar(100) | `productId`. **Kafka 메시지 키로 사용** |
| `event_type` | varchar(100) | `PRODUCT_CREATED` |
| `payload` | text | 이벤트 JSON 원문 |
| `status` | varchar(20) | `PENDING` / `PUBLISHED` |
| `published_at` | timestamp | 발행 시각. 미발행이면 `null` |
| `created_at` / `updated_at` | timestamp | `BaseEntity` 상속 |

`aggregate_type` / `event_type` 은 지금 이벤트가 한 종류라 분기에 쓰이지 않는다. 주문·환불 이벤트가 추가될 때 릴레이를 고치지 않고 토픽을 나눌 수 있도록 미리 자리를 잡아둔 것이다.

---

## 주요 설계 결정

### 1. 서비스가 아니라 어댑터를 교체했다

`ProductCommandService` 는 한 줄도 바뀌지 않았다. `PublishProductEventPort` 인터페이스를 그대로 두고 구현만 `ProductEventPublisherAdapter`(Kafka) → `ProductEventOutboxAdapter`(outbox INSERT)로 바꿨다.

```
ProductCommandService  →  PublishProductEventPort  →  ProductEventOutboxAdapter
   (변경 없음)                 (변경 없음)                    (새 구현)
```

유스케이스는 "이벤트를 발행한다"는 의도만 알고, "어떻게"(직접 발행이냐 outbox냐)는 어댑터 사정이다. `ObjectMapper` 직렬화와 `"PRODUCT"` / `"PRODUCT_CREATED"` 같은 문자열이 어댑터 안에 격리되고, 1주차 `ProductCommandServiceTest` 가 수정 없이 통과한다.

### 2. payload를 문자열 그대로 발행한다

저장할 때 JSON으로 직렬화하고, 발행할 때 그 문자열을 `StringSerializer` 로 그대로 내보낸다. 역직렬화해서 객체로 보내지 않는다.

| | 이유 |
|---|---|
| 기록의 신뢰성 | **저장된 값 == 발행된 값**. outbox에 남은 payload가 실제로 나간 바이트다 |
| 릴레이의 타입 무지 | 릴레이가 `ProductCreatedEvent.class` 를 모른다. 이벤트 종류가 늘어도 릴레이는 그대로다 |

producer의 `value-serializer` 를 `JacksonJsonSerializer` → `StringSerializer` 로 바꿨지만 **stock 컨슈머는 수정하지 않았다.** 둘 다 결국 같은 JSON 바이트를 쓰기 때문이다 — `JacksonJsonSerializer` 는 객체를 JSON 바이트로, `StringSerializer` 는 이미 JSON인 문자열을 UTF-8 바이트로 바꾼다.

### 3. 릴레이에 트랜잭션을 걸지 않았다

이번 주차에서 가장 고민한 지점이다. 릴레이는 `@Transactional` 없이 돌고, 발행이 끝난 건마다 개별 커밋한다.

**한 트랜잭션으로 묶으면 오히려 중복이 늘어난다.** 5번째 발행에서 예외가 나면 트랜잭션이 통째로 롤백되는데, 1~4번은 **이미 Kafka로 나간 뒤**다. 롤백으로 그것들이 `PENDING` 으로 되돌아가 다음 주기에 다시 발행된다.

```
한 트랜잭션                        건당 커밋
─────────────────────            ─────────────────────
1 발행 ✓                          1 발행 ✓ → COMMIT
2 발행 ✓                          2 발행 ✓ → COMMIT
3 발행 ✓                          3 발행 ✓ → COMMIT
4 발행 ✓                          4 발행 ✓ → COMMIT
5 발행 ✗ → 전체 ROLLBACK           5 발행 ✗ → 중단
  1~4 재발행 (중복 4건)               재발행 없음
```

Kafka I/O(최대 10초) 동안 DB 커넥션을 잡지 않는다는 이점도 있다. 과제 요구인 *"Kafka 호출이 트랜잭션 안에 없도록"* 도 릴레이까지 일관되게 지켜진다.

**대가는 행 잠금을 쓸 수 없다는 것이다.** `FOR UPDATE SKIP LOCKED` 는 트랜잭션이 있어야 락을 유지할 수 있다. 그래서 다중 인스턴스에서는 같은 `PENDING` 을 함께 집어 중복 발행이 생긴다 → [02-issues.md](02-issues.md) A-2.

### 4. 발행 실패 시 멈춰서 순서를 지킨다

실패한 건을 건너뛰고 다음으로 넘어가면 순서가 뒤집힌다. 그래서 실패하면 그 자리에서 중단하고, 남은 건은 다음 주기에 처음부터 다시 시도한다.

```java
if (!publish(event)) {
    return;   // 뒤 이벤트는 손대지 않는다
}
```

`send().get(10s)` 로 발행 성공을 **동기로 확인한 뒤에만** `PUBLISHED` 로 바꾼다. 확인 없이 표시하면 유실을 막을 수 없다.

부작용으로, 영구히 실패하는 레코드 하나가 뒤 이벤트를 전부 막는다 → [02-issues.md](02-issues.md) D-1.

### 5. 상태를 enum + 시각으로 나눠 담았다

`published_at` 하나로 `null` = 미발행을 표현할 수도 있었지만 `OutboxStatus` enum을 따로 뒀다. 재시도 횟수 제한과 DLQ가 5주차 과제여서, 그때 `FAILED` 가 들어갈 자리를 미리 만들어둔 것이다.

`markPublished` 는 이미 `PUBLISHED` 인 레코드의 최초 발행 시각을 덮어쓰지 않는다.

---

## 패키지 구조

```
product/
├── domain/outbox/
│   ├── OutboxEvent                      엔티티. markPublished()
│   └── OutboxStatus                     PENDING / PUBLISHED
├── application/
│   ├── port/out/
│   │   ├── PublishProductEventPort       (1주차 그대로)
│   │   ├── OutboxEventPort               save · findPending
│   │   └── PublishMessagePort            publish(topic, key, payload)
│   └── service/
│       ├── ProductCommandService         (1주차 그대로)
│       └── OutboxEventPublisher          @Scheduled 릴레이
└── adapter/out/
    ├── persistence/outbox/
    │   ├── OutboxEventJpaRepository
    │   ├── OutboxEventPersistenceAdapter    OutboxEventPort 구현
    │   └── ProductEventOutboxAdapter        PublishProductEventPort 구현
    └── messaging/
        └── OutboxMessagePublisherAdapter    PublishMessagePort 구현
```

삭제: `adapter/out/messaging/ProductEventPublisherAdapter` — **트랜잭션 안에 `KafkaTemplate` 경로가 남지 않았다.**

`OutboxEvent` 를 `domain` 에 둔 것은 `OutboxEventPort` 가 주고받는 타입이기 때문이다. 어댑터 패키지에 두면 application이 어댑터를 참조하게 된다. 기존 `SaveProductPort.save(Product)` 와 같은 방식이다.

---

## 설정

| 항목 | 값 | 위치 |
|------|-----|------|
| 폴링 주기 | 1000ms | `outbox.relay.fixed-delay` |
| 초기 지연 | 0ms | `outbox.relay.initial-delay` (테스트에서만 크게 잡아 릴레이를 끈다) |
| 배치 크기 | 100건 | `findTop100ByStatusOrderByIdAsc` |
| 발행 타임아웃 | 10초 | `OutboxMessagePublisherAdapter.SEND_TIMEOUT` |

---

## 테스트

| 대상 | 방식 | 확인 |
|------|------|------|
| `OutboxEventTest` | 순수 JUnit | 정적 팩토리 검증, `markPublished` 멱등성 |
| `OutboxEventPersistenceAdapterTest` | `@DataJpaTest` | 미발행 필터, id 정렬, 100건 제한 |
| `ProductEventOutboxAdapterTest` | `@DataJpaTest` | PENDING 기록, payload JSON, 메시지 키 |
| `ProductCommandServiceTransactionTest` | `@SpringBootTest` | **커밋 시 함께 남고, 롤백 시 함께 사라진다** |
| `OutboxEventPublisherTest` | 가짜 포트 + 순수 JUnit | 순서, 실패 시 중단, 다음 주기 재시도 |
| `OutboxIntegrationTest` | `@SpringBootTest` + `@EmbeddedKafka` | 적재 → 발행 → 수신, 저장값 == 발행값 |

---

## 기존 문서와 달라진 점

기록 차원에서만 남긴다. 아래 문서들은 수정하지 않았다.

| 문서 | 차이 |
|------|------|
| `04-roadmap.md` | 아웃박스가 5번 "재시도/재처리" 항목에 있으나 2주차에 선반영됐다 |
| `03-architecture.md` | 네이밍 표에 `EventOutboxAdapter` 가 없다. `domain` 에 기술적 개념(outbox)을 둔 것도 표에 없는 배치다 |
| `week1/01-implementation.md` | 이벤트 계약 표의 직렬화가 `JacksonJsonSerializer` 로 적혀 있다. 실제로는 producer가 `StringSerializer` 다 (바이트는 동일한 JSON이라 컨슈머는 무변경) |
| `week1/01-implementation.md` | 흐름도 A의 ④ `afterCommit` 등록 / ⑤ `send` 단계가 outbox INSERT로 대체됐다 |
