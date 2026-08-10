# 1주차 구현 — 상품 생성 · 재고 등록

## 범위

| 구분 | 내용 |
|------|------|
| API | `POST /admin/products` (상품 생성) · `POST /admin/stocks` (재고 등록) |
| 이벤트 | `ProductCreated` (`product.events`) |

---

## 흐름

| | API | 이벤트 | 역할 |
|---|-----|--------|------|
| **A** | `POST /admin/products` | 비동기 발행 | 상품 생성 + 재고 row 자동 생성 |
| **B** | `POST /admin/stocks` | 없음 (동기) | 재고 수량 누적 |

### A. 상품 생성

```
Admin          product:8083                    Kafka              stock:8084        stock DB
  │ POST /admin/products                         │                    │
  ├──────────────►│                              │                    │
  │               │ ① @Valid 검증                 │                    │
  │               │ ② Product.register() (도메인 불변식 검증)            │
  │               │ ┌─ TX ─────────────┐         │                    │
  │               │ │ ③ INSERT product │         │                    │
  │               │ │ ④ afterCommit 등록 │         │                    │
  │               │ └─ COMMIT ─────────┘         │                    │
  │               │ ⑤ send(key=productId)        │                    │
  │               ├─────────────────────────────►│                    │
  │ 201 {productId}                              │                    │
  │◄──────────────┤                              │                    │
  │               │                              │ ⑥ poll             │
  │               │                              │◄───────────────────┤
  │               │                              │ ⑦ ProductCreated   │
  │               │                              ├───────────────────►│
  │               │                              │                    │ ⑧ exists? No
  │               │                              │                    │ ⑨ INSERT stock(qty=0)
  │               │                              │ ⑩ offset commit    │
  │               │                              │◄───────────────────┤
```

⑤ 는 비동기다. **201 응답은 Kafka 전송 성공 여부와 무관하게 나간다.**

### B. 재고 등록

```
Admin  POST /admin/stocks {productId, quantity}
  └──► stock:8084
         │ findByProductId
         ├─ 없음 ─► 404 UNKNOWN_PRODUCT
         └─ 있음 ─► Stock.add(quantity) → 더티 체킹으로 UPDATE
                    200 {productId, quantity}
```

---

## API 명세

### `POST /admin/products` → 201

```json
{ "name": "티셔츠", "price": 10000 }
```
```json
{ "success": true, "message": "success", "data": { "productId": 1 } }
```

### `POST /admin/stocks` → 200

```json
{ "productId": 1, "quantity": 100 }
```
```json
{ "success": true, "message": "success", "data": { "productId": 1, "quantity": 100 } }
```

| 상황 | 응답 |
|------|------|
| 재고 row 없음 | `404 UNKNOWN_PRODUCT` |
| 입력 검증 실패 | `400 VALIDATION_ERROR` + 필드별 메시지 |
| 메서드 미지원 | `405 METHOD_NOT_ALLOWED` |

---

## 이벤트 계약

| 항목 | 값 |
|------|-----|
| 토픽 | `product.events` |
| 파티션 | 4 |
| 메시지 키 | `productId` (String) |
| 컨슈머 그룹 | `stock-product-events` |
| 직렬화 | `JacksonJsonSerializer` / `JacksonJsonDeserializer` |
| 타입 헤더 | 사용 안 함 (`spring.json.add.type.headers=false`) |

```json
{
  "eventId": "70dfca73-09f0-4603-9b56-0e666f9d82bc",
  "occurredAt": "2026-08-09T12:25:47.547976Z",
  "productId": 1,
  "name": "티셔츠",
  "price": 10000
}
```

`eventId` / `occurredAt` 은 이번 주에 사용하지 않는다. 나중에 추가하면 이미 발행된 메시지와 형식이 어긋나므로 미리 자리를 잡아둔 것이다.

`name` / `price` 는 stock 이 쓰지 않지만 계약에 포함했다. 2주차 `OrderLine` 의 스냅샷에 필요하고, 상품 수정 기능이 없다는 가정이라 복제한 값과 원본 사이의 정합성이 깨질 일이 없다.

---

## 패키지 구조

```
product/                                stock/
├── domain/                             ├── domain/
│   ├── Product                         │   └── Stock
│   └── ProductStatus                   │
├── application/                        ├── application/
│   ├── port/in/                        │   ├── port/in/
│   │   └── RegisterProductUseCase      │   │   ├── CreateStockUseCase
│   ├── port/out/                       │   │   └── AddStockUseCase
│   │   ├── SaveProductPort             │   ├── port/out/
│   │   └── PublishProductEventPort     │   │   ├── LoadStockPort
│   └── service/                        │   │   └── SaveStockPort
│       └── ProductCommandService       │   └── service/
└── adapter/                            │       └── StockCommandService
    ├── in/web/                         └── adapter/
    │   ├── AdminProductController          ├── in/web/
    │   └── dto/                            │   ├── AdminStockController
    └── out/                                │   └── dto/
        ├── persistence/                    ├── in/messaging/
        │   ├── ProductJpaRepository        │   └── ProductEventListener
        │   └── ProductPersistenceAdapter   └── out/persistence/
        └── messaging/                          ├── StockJpaRepository
            └── ProductEventPublisherAdapter    └── StockPersistenceAdapter
```

| 계층 | 책임 |
|------|------|
| `domain` | 엔티티와 불변식. 정적 팩토리로만 생성하고 setter 가 없다 |
| `application.port.in` | 유스케이스 인터페이스. 입력은 Command record |
| `application.port.out` | 저장·발행 인터페이스. 구현은 어댑터가 한다 |
| `application.service` | 유스케이스 구현. 흐름만 담당하고 규칙은 도메인에 맡긴다 |
| `adapter.in.web` | HTTP DTO ↔ Command 번역 |
| `adapter.in.messaging` | Kafka 메시지 → 유스케이스 호출 |
| `adapter.out.*` | `port.out` 구현 (JPA / Kafka) |

`ProductEventListener` 는 `AdminStockController` 와 같은 역할이다. 둘 다 바깥 요청을 유스케이스 호출로 번역하는 인바운드 어댑터이고, 프로토콜만 다르다.

---

## 주요 설계 결정

### 1. 이벤트 발행을 DB 커밋 이후로 미룸

트랜잭션 안에서 발행하면 이후 커밋이 실패해도 이벤트는 이미 나가버려, 존재하지 않는 상품의 재고가 생성된다(유령 이벤트). `TransactionSynchronizationManager` 에 `afterCommit` 콜백을 등록해 커밋이 확정된 뒤에만 발행한다.

트랜잭션 동기화 코드는 `ProductEventPublisherAdapter` 안에 가둬 application 계층은 포트 인터페이스만 알게 유지했다.

### 2. 멱등 컨슈머

Kafka 는 at-least-once 라 같은 이벤트가 중복 전달될 수 있다. `existsByProductId` 로 걸러내고, 실제 보증은 `stock` 테이블의 `product_id` unique 제약이 한다.

메시지 키가 `productId` 라 같은 상품 이벤트는 같은 파티션으로 가고, 컨슈머 그룹 내에서 한 파티션은 한 컨슈머만 처리하므로 동시 처리 자체가 발생하지 않는다.

### 3. 검증을 두 계층에서

| 위치 | 목적 |
|------|------|
| 컨트롤러 `@Valid` | 사용자에게 필드별 400 응답 |
| 도메인 `Product.register()` / `Stock.add()` | HTTP 를 거치지 않는 경로에서도 규칙 유지 |
