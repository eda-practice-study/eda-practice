# 주문 API (order)

## 주문 생성 — `POST /api/orders`

stock 에 재고 예약(TCC Try)을 동기로 요청해 주문 가능 여부를 바로 판단하고, 주문과 주문 생성 이벤트를 한 트랜잭션으로 저장한다. Stock DB 차감(Confirm)은 이벤트로 나중에 처리하므로 응답은 이를 기다리지 않는다. 흐름은 [stock-reservation-tcc.md](../stock-reservation-tcc.md) 참고.

**Request**

| 헤더 | 규칙 |
|------|------|
| X-Member-Id | 필수, 회원 ID (mock 인증) |

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1 }
  ]
}
```

| 필드 | 타입 | 규칙 |
|------|------|------|
| items | Array | 필수, `1 ~ 20` 개, 같은 `productId` 중복 불가 |
| items[].productId | Long | 필수 |
| items[].quantity | Integer | 필수, `1 ~ 10` |

**Response** `201 Created`

```json
{
  "success": true,
  "message": "success",
  "data": {
    "orderId": 1,
    "status": "CREATED",
    "totalAmount": 75000,
    "items": [
      { "productId": 1, "productName": "키보드", "unitPrice": 30000, "quantity": 2 },
      { "productId": 2, "productName": "마우스", "unitPrice": 15000, "quantity": 1 }
    ],
    "createdAt": "2026-11-16T10:00:00"
  }
}
```

| 필드 | 의미 |
|------|------|
| productName · unitPrice | 주문 시점의 상품 스냅샷 값 (product 를 호출하지 않음) |
| totalAmount | `unitPrice × quantity` 의 합 |

**Error**

| 상황 | 응답 |
|------|------|
| `X-Member-Id` 누락 · items 누락 · 범위 오류 · `productId` 중복 | `400 VALIDATION_ERROR` |
| 상품 스냅샷이 없는 상품 (상품 생성 이벤트 반영 전 포함) | `404 PRODUCT_NOT_FOUND` |
| Redis 재고 부족 (재고 키가 없는 경우 포함) | `409 OUT_OF_STOCK` |
| stock 호출 실패 · 타임아웃 (같은 예약 ID 로 3번 시도 후) | `503 STOCK_SERVICE_UNAVAILABLE` |

- `503` · 주문 저장 실패로 응답할 때, 예약이 stock 에 남았을 수 있으면 예약 취소를 요청한다 (`ReservationCancelRequested`)

## 발행 이벤트 (`order.events`)

| 이벤트 | 키 | 언제 | payload |
|--------|-----|------|---------|
| `OrderCreated` | orderId | 주문 저장과 같은 트랜잭션 | `orderId, reservationId, memberId, items[productId, quantity], totalAmount` |
| `ReservationCancelRequested` | reservationId | 예약 후 주문을 만들지 못함 · 예약 결과를 모름 | `reservationId` |
