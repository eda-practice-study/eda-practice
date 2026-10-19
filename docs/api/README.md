# API 명세

## 문서

| 문서 | 서비스 | API |
|------|--------|-----|
| [product.md](product.md) | product | 상품 생성 · 상품 목록 조회 |
| [stock.md](stock.md) | stock | 재고 증가 · 재고 조회 · 내부 API |

---

## 공통

### 서비스 주소

| 서비스 | 주소 | 비고 |
|--------|------|------|
| order | `http://localhost:8081` | 주문 · 환불 · Saga 오케스트레이터 |
| payment | `http://localhost:8082` | 결제 승인 (Mock PG) |
| product | `http://localhost:8083` | 상품 |
| stock | `http://localhost:8084` | 재고 (DB + Redis) |
| notification | `http://localhost:8085` | 알림 mock (이벤트 구독). 웹은 헬스 체크용 |

### 경로 규칙

- 사용자 API: `/api/**`
- 서비스 간 동기 호출: `/internal/**`

### 헤더

| 헤더 | 대상 | 설명 |
|------|------|------|
| `X-Member-Id` | 주문 · 결제 API | 회원 ID (mock 인증). 없으면 `400 VALIDATION_ERROR` |
| `Content-Type: application/json` | 요청 바디가 있는 API | |

### 응답 형식

성공 — `ApiResponse<T>`

```json
{ "success": true, "message": "success", "data": { } }
```

목록 — `ApiResponse<PageResponse<T>>`

```json
{
  "success": true,
  "message": "success",
  "data": { "content": [ ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
}
```

실패 — `ErrorResponse`

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "입력값이 올바르지 않습니다",
  "errors": { "items[0].quantity": "10 이하여야 합니다" }
}
```

---

## API 요약

| # | 서비스 | 기능 | METHOD | URI | 성공 |
|---|--------|------|--------|-----|------|
| 1 | product | 상품 생성 | POST | `/api/products` | 201 |
| 2 | product | 상품 목록 조회 | GET | `/api/products` | 200 |
| 3 | stock | 재고 증가 | POST | `/api/stocks/{productId}/increase` | 200 |
| 4 | stock | 재고 조회 (검증용) | GET | `/api/stocks/{productId}` | 200 |
| 5 | order | 주문 | POST | `/api/orders` | 201 |
| 6 | order | 주문 목록 조회 | GET | `/api/orders` | 200 |
| 7 | order | 주문 상세 조회 | GET | `/api/orders/{orderId}` | 200 |
| 8 | order | 환불 | POST | `/api/orders/{orderId}/refund` | 202 |
| 9 | payment | 결제 승인 | POST | `/api/payments/confirm` | 200 / 202 |
| 10 | 컨슈머 서비스 공통 | DLQ 목록 조회 | GET | `/api/dead-letters` | 200 |
| 11 | 컨슈머 서비스 공통 | DLQ 상세 조회 | GET | `/api/dead-letters/{id}` | 200 |
| 12 | 컨슈머 서비스 공통 | DLQ 재처리 | POST | `/api/dead-letters/{id}/replay` | 200 |
| 13 | 컨슈머 서비스 공통 | DLQ 폐기 | POST | `/api/dead-letters/{id}/discard` | 200 |

**내부 API (서비스 간 동기 호출)**

| 호출 | METHOD | URI | 용도 |
|------|--------|-----|------|
| product → stock | GET | `/internal/stocks?productIds=` | 상품 목록에 가용 재고 붙이기 |
---
