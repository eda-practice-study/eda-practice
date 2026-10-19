# eda-practice

EDA(이벤트 기반) MSA 스터디 프로젝트. `주문 → 재고 차감 → 결제` 흐름을 구현하면서 아래 주제를 학습한다.

- 트래픽이 몰릴 때 실시간 재고 동시성 처리 (Redis Lua, k6 부하 테스트)
- Transactional Outbox 패턴
- 재시도, DLQ 재처리, 멱등 컨슈머
- MSA 환경에서의 Kafka 메시지 처리
- 결제 Saga 와 보상 트랜잭션 (결제 실패 → 재고 복원 → 주문 취소)
- 관측 가능성: OpenTelemetry, Tempo, Prometheus, Loki, Grafana

## 모듈

| 모듈 | 포트 | 설명 |
|------|------|------|
| `common` | – | 공통 (예외, 응답, 메시지 규격, Outbox relay) |
| `order` | 8081 | 주문 · 환불 · Saga 오케스트레이터 (REST) |
| `payment` | 8082 | 결제 승인, Mock PG (REST) |
| `product` | 8083 | 상품 (REST) |
| `stock` | 8084 | 재고 (REST + Redis) |
| `notification` | 8085 | 모든 `*.events` 를 받아 로그로 남기는 알림 mock |

## 기술 스택

- Java 21
- Spring Boot 4.1
- Gradle 9.5 (Kotlin DSL, 멀티모듈)
- PostgreSQL 17
- Apache Kafka (KRaft)
- Redis (이미지는 Valkey)
- OpenTelemetry
- Grafana (Tempo, Prometheus, Loki)
- k6
- Docker, Docker Compose

## 실행

```bash
# 전 모듈 빌드 + 테스트
./gradlew build

# 앱 이미지 빌드 (Jib, 로컬 Docker 에 eda-<서비스> 이미지 생성)
./gradlew jibDockerBuild

# 전체 스택 컨테이너
docker compose up -d

# 필요한 서비스만
./gradlew :order:bootRun
```

## 문서

| 문서 | 내용 |
|------|------|
| [docs/api/](docs/api/README.md) | API 명세|
| [docs/outbox.md](docs/outbox.md) | Transactional Outbox|

