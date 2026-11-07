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
| availableQuantity | Redis | DB 커밋 후 Redis에 반영한 가용 재고. Redis 반영에 실패하면 `null`로 응답하며, Outbox 이벤트로 재반영할 수 있다. |

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
| availableQuantity | Redis | 가용 재고. 키가 없거나 버전이 뒤처지면 DB 스냅샷으로 복구한다. Redis에 연결할 수 없으면 `null` |
| consistent | 계산 | 수량과 버전이 DB 스냅샷과 일치하는지 |

**Error**: `404 STOCK_NOT_FOUND`

---

## 내부 API

stock 이 다른 서비스에 제공하는 동기 API. 사용자가 직접 호출하지 않는다.

| 호출 | METHOD | URI | 설명 |
|------|--------|-----|------|
| product → stock | GET | `/internal/stocks?productIds=` | 상품 목록에 재고 조회 |
