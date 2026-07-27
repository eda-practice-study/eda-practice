# 용어 사전

## 도메인 용어

| 한글 | 영어 | 설명                                      |
|------|------|-----------------------------------------|
| 회원 | Member | 주문·환불을 요청하는 사용자 (mock, `X-Member-Id`) |
| 상품 | Product | 판매 상품                                   |
| 재고 | Stock | 상품별 수량                                  |
| 주문 | Order | 회원이 여러 상품을 한 번에 구매하는 요청                 |
| 주문 라인 | OrderLine | 주문 내 상품별 항목                             |
| 결제 | Payment | 주문 금액 결제 (mock)                         |
| 환불 | Refund | 결제 취소, 재고 복원                            |

---

# 도메인 모델

- 4개 애그리거트: **Product · Stock · Order · Payment** — 각 서비스가 소유.
- **애그리거트 간 참조는 `id` 값으로만** (FK·객체 참조 없음).
- 생성은 **정적 팩토리**, 상태 변경은 **행위 메서드**로만 (setter 없음). 모든 엔티티는 `BaseEntity`(id + 생성/수정 시각) 상속.

## [상품] Product _(Aggregate Root)_

| 속성 | 타입 | 규칙 |
|------|------|------|
| name | String | not blank |
| price | BigDecimal | > 0 |
| status | ProductStatus |  |

## [재고] Stock _(Aggregate Root, 초안)_

| 속성 | 타입 | 규칙 |
|------|------|------|
| productId | Long | 값 참조 (unique) |
| quantity | int | ≥ 0 |

> TODO: 추후 `available`/`reserved` 분리로 예약/확정 정밀화.

## [주문] Order _(Aggregate Root)_ + OrderLine _(Entity)_

**Order**

| 속성 | 타입 |
|------|------|
| memberId | Long (값 참조) |
| status | OrderStatus |
| totalAmount | BigDecimal (= Σ 라인 소계) |
| orderLines | List\<OrderLine\> (내부 소유) |

**OrderLine** (Order 통해서만 조작)

| 속성 | 타입 |
|------|------|
| productId | Long (값 참조) |
| productName / unitPrice | **스냅샷** |
| quantity / refundedQuantity | int |

## [결제] Payment _(Aggregate Root)_

| 속성 | 타입 | 규칙 |
|------|------|------|
| paymentKey | String | **멱등 키** (unique) |
| orderId | Long (값 참조) | |
| amount | BigDecimal | > 0 |
| status | PaymentStatus | |
| refundedAmount | BigDecimal | ≤ amount |

## 상태 (Enum)

| Enum | 값                                                                                         |
|------|-------------------------------------------------------------------------------------------|
| **OrderStatus** | CREATED -> STOCK_RESERVED -> PAID -> COMPLETED / CANCELED / PARTIALLY_REFUNDED / REFUNDED |
| **PaymentStatus** | PENDING -> PAID / FAILED, PAID -> PARTIALLY_REFUNDED / REFUNDED                           |
| **ProductStatus** | ACTIVE / DEACTIVATED                                                                      |

## 공통 (common)

- **BaseEntity**: id, createdAt, updatedAt (JPA Auditing)
- **ErrorCode / BusinessException**: 도메인 예외 — VALIDATION_ERROR, INTERNAL_ERROR, INSUFFICIENT_STOCK, INVALID_STOCK_OPERATION, INVALID_ORDER_STATUS, INVALID_REFUND, PAYMENT_FAILED, INVALID_PAYMENT_STATUS
