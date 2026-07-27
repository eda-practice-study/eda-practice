# 요구사항

> EDA 스터디: `주문 → 재고 차감 → 결제 → 확정` flow를 이벤트 기반으로 구현.
> 서비스 간에는 **Kafka 이벤트로만** 통신, 각자 **독립 스키마**. 인증은 mock(`X-Member-Id`).

## API 요약

| 구분 | 기능 | METHOD | URI |
|------|------|--------|-----|
| 어드민 | 상품 생성 | POST | `/admin/products` |
| 어드민 | 재고 등록 | POST | `/admin/stocks` |
| 사용자 | 주문 생성 | POST | `/orders` |
| 사용자 | 주문 조회 | GET | `/orders/{orderId}` |
| 사용자 | 환불 요청 | POST | `/orders/{orderId}/refunds` |
| 사용자 | 상품 목록 | GET | `/products` |

---

## 기능별 상태 변경 flow

표기: `[서비스] 도메인동작 → 결과상태`. 화살표(↓)는 이벤트 전파.

### 1. 상품 생성 — `POST /admin/products`
제약: `name` 필수, `price > 0`
```
[Product] register → ACTIVE
   ↓ (ProductCreated 이벤트)
[Stock]   createFor(productId) → 재고 0 생성
```

### 2. 재고 등록 — `POST /admin/stocks`
제약: `productId` 존재, `quantity > 0`
```
[Stock] add(quantity) → 재고 증가
```

### 3. 주문 생성 — `POST /orders` (헤더 `X-Member-Id`)
```
[Order] create → CREATED
   ↓ 재고 차감
[Stock] deduct(qty)   성공 → [Order] markStockReserved → STOCK_RESERVED
                      실패 → [Order] cancel → CANCELED  (종료)
   ↓ 결제
[Payment] create → PENDING → complete → PAID  성공 → [Order] markPaid → PAID
                            → fail    → FAILED 실패 → [Stock] restore(보상) + [Order] cancel → CANCELED
   ↓ 확정
[Order] markCompleted → COMPLETED
```

### 4. 주문 조회 — `GET /orders/{orderId}`

### 5. 환불 요청 — `POST /orders/{orderId}/refunds` (**진입점 = Order**)
```
[Order] 검증 (상태 · 라인별 환불가능수량)
   ↓ 결제 환불
[Payment] refund(amount) → PARTIALLY_REFUNDED / REFUNDED
   ↓ 재고 복원
[Stock] restore(qty)
   ↓ 주문 갱신
[Order] refund → PARTIALLY_REFUNDED / REFUNDED
```
> 순서: **돈 먼저(Payment) → 재고(Stock) → 주문(Order)**.

### 6. 상품 목록 — `GET /products`
상태 변경 없음. 판매 중(ACTIVE) 상품 조회.

---

## 주문 상태 전이

```
CREATED ──재고차감──> STOCK_RESERVED ──결제성공──> PAID ──확정──> COMPLETED
   │                     │                                      ├─부분환불─> PARTIALLY_REFUNDED
   └─재고부족─> CANCELED   └─결제실패→재고복원─> CANCELED              └─전량환불─> REFUNDED
PARTIALLY_REFUNDED ──잔여전량환불──> REFUNDED
```

| 실패 지점 | 보상 |
|-----------|------|
| 재고 차감 실패 | 없음 (주문만 CANCELED) |
| 결제 실패 | 재고 복원 → 주문 CANCELED |
| 환불 실패 | 주문 상태 유지, 환불만 실패 처리 |
