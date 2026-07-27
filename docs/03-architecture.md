# 아키텍처 사전

## 스타일

- **EDA(Event-Driven)** — 서비스 간 통신은 **Kafka 이벤트만**. 각자 독립 스키마.
- **헥사고날 + 도메인 모델 패턴** — 도메인 중심, 바깥(REST·Kafka·DB)은 어댑터. 의존은 **항상 안쪽으로**.

```
외부(Actor) → adapter → application(port) → domain
```

## 모듈 구조 (Gradle 멀티모듈)

```
eda-practice/
├── common/     # 공유 라이브러리 (BaseEntity·예외·응답·이벤트 계약)
├── order/      # 8081, REST
├── payment/    # 8082, 이벤트 전용(웹 없음)
├── product/    # 8083, REST
└── stock/      # 8084, REST
```

- 서비스 → **common 단방향**. 서비스끼리 코드 의존 **금지**.
- 루트 패키지 `com.eda.<module>`.

## 서비스 내부 표준 구조

| 계층 | 패키지 | 책임 |
|------|--------|------|
| **domain** | `domain` | 엔티티·상태머신·불변식 (순수 Java) |
| **application** | `application.port.in` | 유스케이스 인터페이스 (인바운드 포트) |
| | `application.port.out` | 저장·발행 인터페이스 (아웃바운드 포트) |
| | `application.service` | 유스케이스 구현 |
| **adapter** | `adapter.in.web` | REST 컨트롤러 |
| | `adapter.in.messaging` | Kafka 컨슈머 |
| | `adapter.out.persistence` | JPA 저장소 (`port.out` 구현) |
| | `adapter.out.messaging` | Kafka 프로듀서 (`port.out` 구현) |

> 현재 **domain 계층까지 구현**. 이후 application → adapter 순으로 확장.

## 의존 방향 규칙 (리뷰 포인트)

```
adapter.in.*  → port.in → service → domain
adapter.out.* ─(구현)→ port.out ┘
모든 서비스 → common
```

- `domain`은 Spring·JPA·Kafka를 **몰라도 되게** 지향 (초반엔 JPA 애노테이션 겸용 허용).
- `service`는 어댑터 클래스를 참조하지 않는다 (`port.out` 인터페이스로만).
- Kafka 코드(`@KafkaListener`·`KafkaTemplate`)는 `adapter.*.messaging`에만.
- 애그리거트 간 참조는 **`id` 값**으로만. FK·객체 참조 금지.

## 네이밍 컨벤션

| 대상 | 규칙 | 예 |
|------|------|-----|
| 인바운드 포트 | `<동사><명사>UseCase` | `CreateOrderUseCase` |
| 아웃바운드 포트 | `<동사>...Port` | `SaveOrderPort` |
| 유스케이스 구현 | `<명사>Service` | `OrderCommandService` |
| 영속성 어댑터 | `<명사>PersistenceAdapter` | `OrderPersistenceAdapter` |
| 이벤트 구독/발행 | `<Service>EventListener` / `EventPublisherAdapter` | `StockEventListener` |
| Kafka 토픽 / 컨슈머 그룹 | `<domain>.events` / `<service>-<purpose>` | `order.events` |

## 도메인 코딩 컨벤션

- **캡슐화**: `private` 생성자 + 정적 팩토리. **public setter 금지**. 내부 엔티티는 루트를 통해서만 조작(package-private).
- **불변식은 생성 시점에** 검증 → always-valid 객체.
- **값 참조**: 애그리거트 간 `Long id`. **스냅샷**: 표시값은 시점 복사.
- **예외**: 규칙 위반은 `BusinessException(ErrorCode)`.

## 기술 스택

| 항목 | 값 |
|------|-----|
| Java | 21 |
| Framework | Spring Boot 4.1 |
| Build | Gradle 9.5 (Kotlin DSL, 멀티모듈) |
| DB | PostgreSQL 17 |
| Messaging | Apache Kafka (KRaft) |
| 컨테이너 | Docker Compose |

## DB — 단일 DB + 스키마 격리

- **Postgres 1개 + DB 1개(`eda`) + 서비스별 스키마**로 논리 격리.
```yaml
spring.jpa:
  hibernate.ddl-auto: update            # 테이블 자동 생성
  properties.hibernate:
    default_schema: order               # 서비스별
    globally_quoted_identifiers: true   # 예약어(order 등) 대응
    hbm2ddl.create_namespaces: true     # 스키마 자동 생성
```

## common 규칙

- `common`엔 **공유 계약**만: BaseEntity, 예외 체계, 응답 DTO, 이벤트 계약 `com.eda.common.event`. **도메인 로직 금지.**
- web 의존은 **`compileOnly`** → 소비자에 전이 안 됨. `GlobalExceptionHandler`는 각 웹 서비스가 `@Import` 로 등록.

## 빌드 · 실행 · 테스트

```bash
./gradlew build                        # 전 모듈 컴파일 + 테스트
docker compose up -d postgres kafka    # 인프라만 (로컬 권장)
./gradlew :order:bootRun               # 필요한 서비스만
docker compose up --build              # 전체 스택 컨테이너
```

| 테스트 | 방식 |
|--------|------|
| 도메인 단위 | 순수 JUnit5 + AssertJ (given/when/then, 한글 @DisplayName) |
| 컨텍스트 로딩 | `@SpringBootTest` + H2 (docker 불필요) |
