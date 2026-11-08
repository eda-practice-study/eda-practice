# 0009 요청 종류와 멱등성 키로 공통 요청 이력을 관리한다

- 상태: 채택
- 적용 범위: stock HTTP 요청의 중복 방지
- 기록일: 2026-11-08
- 이전 결정: [0008](0008-stock-increase-duplicate-rejection.md)

## 배경과 결정

API마다 이력 테이블을 추가하는 대신 stock 내부의 `request_history`를 공유한다.
`(request_type, idempotency_key)`를 복합 PK로 사용하고 생성 시각만 저장한다.
숫자 ID와 상품 ID는 중복 판단에 필요하지 않아 제거한다. 요청 종류는 서버가 지정하며 현재는 `STOCK_INCREASE`만 사용한다.
이력은 `Persistable`로 신규 INSERT하고 재고·Outbox와 함께 커밋한다. 중복 요청 거절과 Redis 실패 허용은 유지한다.

## 위험과 검증

기존 키를 이전하지 않으면 중복 증가할 수 있으므로 서비스 중지 상태에서 [이전 절차](../runbook.md#재고-증가-확인)를 수행한다.
종류별 키 재사용, 동시 중복 차단, 롤백과 이전 후 중복 거절을 PostgreSQL 통합 테스트로 확인한다.
서비스 간 테이블 공유와 키 만료 정책은 이번 범위에 포함하지 않는다.
