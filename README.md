# eda-practice

## 1주차 정리 (및 공부) 
https://kang-log.org/posts/%EA%B3%B5%EB%B6%80/msa-%EA%B5%AC%ED%98%84/product-stock/

EDA(이벤트 기반) MSA 스터디용 프로젝트. `주문 → 재고 차감 → 결제 → 확정` 흐름을 Kafka 이벤트로 구현하며 아래를 학습한다.

- MSA에서의 Kafka 메시지 처리
- Transactional Outbox 패턴
- 재시도 & DLQ 재처리, 멱등 컨슈머
- 실시간 재고 동시성 처리
- 관측(트레이싱 / 메트릭 / 로깅)

## 모듈

| 모듈 | 포트 | 설명                       |
|------|------|--------------------------|
| `common` | – | 공통 (BaseEntity, 예외, 이벤트) |
| `order` | 8081 | 주문 · 환불 (REST)           |
| `payment` | 8082 | 결제 (이벤트 전용, 웹 없음)        |
| `product` | 8083 | 상품 (REST)                |
| `stock` | 8084 | 재고 (REST)                |

- 서비스 간 통신은 **Kafka 이벤트만**, 각자 **독립 스키마**.
- 아키텍처: 헥사고날 + 도메인 모델 패턴 / 애그리거트 간 참조는 `id` 값으로만.

## 기술 스택

Java 21 · Spring Boot 4.1 · Gradle 9.5 (Kotlin DSL, 멀티모듈) · PostgreSQL 17 · Apache Kafka(KRaft) · Docker Compose

## 실행

```bash
# 전 모듈 빌드 + 테스트
./gradlew build

# 전체 스택 컨테이너
docker compose up --build

# 필요한 서비스만
./gradlew :order:bootRun
```

## 문서

| 문서 | 내용 |
|------|------|
| [docs/01-domain-model.md](docs/01-domain-model.md) | 용어 사전 · 도메인 모델 |
| [docs/02-requirements.md](docs/02-requirements.md) | 요구사항 · API · 상태 전이 flow |
| [docs/03-architecture.md](docs/03-architecture.md) | 아키텍처 · 모듈/패키지 구조 · 컨벤션 |
| [docs/04-roadmap.md](docs/04-roadmap.md) | 로드맵 |
