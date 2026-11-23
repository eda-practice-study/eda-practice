# 재고 API (stock)

## 재고 증가 — `POST /api/stocks/{productId}/increase`

**Request**

```json
{ "quantity": 100 }
```

| 필드 | 타입 | 규칙 |
|------|------|------|
| quantity | int | 필수, `1 ~ 10,000` |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "success",
  "data": { "productId": 1, "quantity": 100, "availableQuantity": 100 }
}
```

| 필드 | 출처 | 의미 |
|------|------|------|
| quantity | DB | 증가 후 실재고 |
| availableQuantity | Redis | 증가 후 가용 재고. Redis 반영에 실패하면 `null` (DB 는 반영됨) |

**Error**

| 상황 | 응답 |
|------|------|
| quantity 누락 · 범위 오류 | `400 VALIDATION_ERROR` |
| 재고 행 없음 | `404 STOCK_NOT_FOUND` |

---

## 재고 조회 (검증용) — `GET /api/stocks/{productId}`

**Response** `200 OK`

```json
{
  "success": true,
  "message": "success",
  "data": {
    "productId": 1,
    "quantity": 95,
    "availableQuantity": 95,
    "consistent": true
  }
}
```

| 필드 | 출처 | 의미 |
|------|------|------|
| quantity | DB | 실재고 |
| availableQuantity | Redis | 가용 재고. 키가 없으면 `null` |
| consistent | 계산 | `quantity == availableQuantity` 인지 |

**Error**: `404 STOCK_NOT_FOUND`

---

## 내부 API

stock 이 다른 서비스에 제공하는 동기 API. 사용자가 직접 호출하지 않는다.

| 호출 | METHOD | URI | 설명 |
|------|--------|-----|------|
| product → stock | GET | `/internal/stocks?productIds=` | 상품 목록에 재고 조회 |
| order → stock | POST | `/internal/stocks/reservations` | 재고 예약 (TCC Try) |
| order → stock | POST | `/internal/stocks/reservations/{reservationId}/cancel` | 재고 예약 취소 (TCC Cancel) |

### 재고 예약 TCC

| 단계 | 호출 | Redis |
|------|------|-------|
| Try | order → `POST /internal/stocks/reservations` (동기, 결과를 모르면 같은 ID 로 최대 3번) | `available` → `reserved`, `reservation:{id}` 에 상품별 수량 기록 |
| Confirm | `OrderCreated` 이벤트 수신 → Stock DB 차감 커밋 후 | `reserved` 에서 뺌, 기록은 `CONFIRMED` 로 남김 |
| Cancel | `ReservationCancelRequested` 이벤트 · cancel API · 만료 스케줄러 | 기록된 수량만 `reserved` → `available` |

- 모든 단계는 `reservationId` 로 멱등하다. 같은 ID 로 다시 와도 한 번만 반영한다
- 빈 롤백: Try 보다 Cancel 이 먼저 오면 `CANCELLED` 표식만 남긴다
- 매달림 방지: `CANCELLED` 표식이 있는 ID 의 Try 는 `410 RESERVATION_CANCELLED` 로 거부한다
- 확정 · 취소된 기록은 `stock.reservation.record-ttl` (기본 1일) 동안 남긴다
- `stock.reservation.timeout` (기본 10분) 넘게 남은 예약은 만료 스케줄러가 취소한다

**Try Request**

```json
{
  "reservationId": "3f0c1b2e-8a4d-4c7e-9b1a-2d5e6f7a8b9c",
  "items": [ { "productId": 1, "quantity": 2 } ]
}
```

| 상황 | 응답 |
|------|------|
| 예약함 (이미 예약 · 확정된 ID 포함) | `200 OK` |
| 재고 부족 · 재고 키 없음 | `409 OUT_OF_STOCK` (아무것도 바꾸지 않음) |
| 이미 취소된 ID | `410 RESERVATION_CANCELLED` |
| 필드 누락 · 범위 오류 · productId 중복 | `400 VALIDATION_ERROR` |
