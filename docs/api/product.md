# 상품 API (product)

## 상품 생성 — `POST /api/products`

**Request**

```json
{ "name": "키보드", "price": 30000 }
```

| 필드 | 타입 | 규칙 |
|------|------|------|
| name | String | 필수, 공백 불가, 최대 100자, 중복 금지 |
| price | Integer | 필수, 원 단위 정수, `1 ~ 1,000,000` |

- 초기 재고는 받지 않는다. 재고는 재고 증가로만 넣는다.

**Response** `201 Created`

```json
{
  "success": true,
  "message": "success",
  "data": { "productId": 1, "name": "키보드", "price": 30000, "createdAt": "2026-10-20T10:00:00" }
}
```

**Error**

| 상황 | 응답 |
|------|------|
| name · price 누락 · 범위 오류 | `400 VALIDATION_ERROR` |
| 같은 이름의 상품이 이미 있음 | `409 PRODUCT_NAME_DUPLICATED` |

---

## 상품 목록 조회 — `GET /api/products?page=0&size=20`

상품 한 페이지를 조회한 뒤, stock 에 **한 번에 묶어** 가용 재고를 조회해 합친다.


| 파라미터 | 기본값 | 규칙 |
|----------|--------|------|
| page | 0 | `≥ 0` |
| size | 20 | `1 ~ 100` |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "success",
  "data": {
    "content": [
      { "productId": 3, "name": "모니터", "price": 250000, "availableQuantity": null },
      { "productId": 2, "name": "마우스", "price": 15000, "availableQuantity": 0 },
      { "productId": 1, "name": "키보드", "price": 30000, "availableQuantity": 92 }
    ],
    "page": 0, "size": 20, "totalElements": 3, "totalPages": 1
  }
}
```

| availableQuantity | 의미 |
|-------------------|------|
| `0` 이상 | Redis 가용 수량 (지금 주문 가능한 수량). `0` = 품절 |
| `null` | 재고를 알 수 없음 — Redis 에 키 없음(이벤트 미수신 · Redis 재시작) 또는 stock 호출 실패 |

- stock 호출이 실패하거나 타임아웃이 나도 **상품 목록은 정상 응답**한다 (부분 응답).

**Error**: `400 VALIDATION_ERROR` (page · size 범위)
