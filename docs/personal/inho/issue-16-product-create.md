# 첫 번째 이슈 작업 기록 — 상품 생성 API

작성일 2026-11-07
상태 수정 가능한 작업 기록

이 문서는 상품 생성 API 구현과 오류 해결 과정의 초기 기록이다.

2026-11-08에 이슈 #16과 #17 본문을 확인했다. #16에서 요구한 stock 재고 초기화, order 상품 스냅샷, 두 등록 이벤트를 추가했다. #17에 맞춰 재고 증가 이벤트를 stock.events로 발행하고, Redis 반영에 성공하면 가용 수량을 반환하도록 수정했다. 아래에서 후속 작업으로 설명한 항목은 초기 구현 시점의 범위를 기록한 것이다.

## 1. 작업 배경과 범위

상품 생성 요청을 받아 상품을 저장하고, Kafka 이벤트를 통해 stock이 재고를 초기화하는 흐름을 준비했다. order에서도 사용할 정보이므로 정합성과 응답 속도를 함께 고려했다.

이번 구현 범위는 상품 생성 HTTP API와 ProductCreated 이벤트의 Outbox 기록이다. 기존 Relay를 재사용해 Kafka에 발행한다.

상품 목록 조회, stock의 이벤트 소비와 DB·Valkey 초기화, order 연동은 후속 작업이다.

## 2. 개발 환경에서 발생한 문제

### zsh 설정 파일 실행 오류

터미널에서 ~/.zprofile 경로를 명령으로 실행했을 때 permission denied가 발생했다.

현재 셸에 설정을 적용하려면 아래와 같이 읽어 들인다.

```zsh
source ~/.zprofile
brew --version
```

Homebrew 환경 설정에 사용한 명령은 아래와 같다.

```zsh
eval "$(/opt/homebrew/bin/brew shellenv zsh)"
```

### Docker 엔진 연결 실패

sbx 설치 후 Jib 이미지 빌드와 Docker Compose 실행 과정에서 Docker API 연결 오류가 발생했다.

```text
failed to connect to the docker API at unix:///var/run/docker.sock
connect: no such file or directory
```

sbx 설치와 별도로 Docker Desktop 엔진의 실행 상태와 Docker 컨텍스트를 확인했다. 안내한 복구 순서는 아래와 같다.

```zsh
open -a Docker

# 엔진 시작 후 확인
docker info

# 엔진이 실행 중인데도 기본 소켓 연결이 실패할 경우
export DOCKER_CONTEXT=desktop-linux
docker info
```

이후 사용자가 컨테이너 5개가 정상 실행됐다고 확인했다. 어떤 단계가 최종 해결에 필요했는지는 별도 실행 기록으로 남기지 않았다.

빌드 실패 시 후속 명령이 실행되지 않도록 다음과 같이 연결한다.

```zsh
./gradlew :product:jibDockerBuild :stock:jibDockerBuild &&
  docker compose up -d product stock &&
  docker compose ps
```

## 3. 정합성과 속도에 대한 검토

아래는 구현을 확장할 때 사용할 설계 제안이다. 확정된 요구사항과 구분해서 검토한다.

| 구간 | 제안한 기준 |
| --- | --- |
| 상품 생성 | 상품과 발행할 이벤트를 같은 DB 트랜잭션으로 저장 |
| 재고 초기화와 조회 | 이벤트 반영 지연을 허용하고, 초기화 대기와 품절을 구분 |
| 주문 재고 예약 | 동시 요청에서 초과 판매 방지, 중복 요청의 중복 차감 방지 |
| DB와 Valkey | PostgreSQL을 기준으로 시작하고, Valkey 반영 실패 시 복구 경로 마련 |

상품 생성 성공 응답은 상품과 Outbox 기록의 커밋을 의미한다. stock의 초기화 완료까지 보장하려면 별도의 준비 상태와 주문 허용 기준을 정의해야 한다.

ProductCreated 이벤트가 재전달되어도 기존 재고를 0으로 덮어쓰지 않도록 초기화의 멱등성을 구현해야 한다. Kafka 재전달, 소비자 재시작, DB 커밋 이후 Valkey 반영 실패를 검증 대상으로 잡는다.

주문 예약은 stock이 소유하고, PostgreSQL의 조건부 갱신으로 수량 확인과 차감을 원자적으로 처리하는 방안을 제안했다. Valkey Lua를 사용하는 예약 방식은 부하 측정과 장애 복구 요구사항을 확인한 후 비교한다.

## 4. 상품 생성 API 구현

### 소스 배치

소스는 product/src/main/java/com/eda/product 아래에 배치했다. product/build는 빌드 산출물 디렉터리로, clean 작업 시 삭제될 수 있다.

```text
product/src/main/java/com/eda/product/
├── ProductApplication.java
├── controller/
│   └── ProductController.java
├── domain/
│   └── Product.java
├── dto/
│   ├── CreateProductRequest.java
│   └── CreateProductResponse.java
├── event/
│   └── ProductCreated.java
├── repository/
│   └── ProductRepository.java
└── service/
    └── ProductService.java
```

### 처리 흐름

1. ProductController가 POST /api/products 요청을 받는다.
2. 요청 DTO와 기존 Product.register 로직으로 입력을 검증한다.
3. ProductService의 트랜잭션 안에서 상품을 저장하고 flush한다.
4. 같은 트랜잭션에서 기존 OutboxWriter로 ProductCreated 이벤트를 저장한다.
5. 커밋 성공 후 201 응답을 반환한다.
6. 기존 Outbox Relay가 product.events 토픽으로 메시지를 발행한다.

Kafka 메시지 키는 상품 ID 문자열이다. 이벤트 본문에는 productId, name, price를 담는다. 메시지 ID, 타입, 발생 시각은 기존 공통 메시지·Outbox 규격을 따른다.

### 요청과 응답

```http
POST /api/products
Content-Type: application/json
```

```json
{"name":"테스트 키보드","price":30000}
```

성공 시 201과 공통 ApiResponse를 반환한다. data에는 productId, name, price, createdAt을 담는다.

| 조건 | 처리 |
| --- | --- |
| 상품명 누락, 공백, 100자 초과 | 400 VALIDATION_ERROR |
| 가격 누락, 소수, 1 미만, 1,000,000 초과 | 400 VALIDATION_ERROR |
| 잘못된 JSON 또는 읽을 수 없는 요청 본문 | 400 VALIDATION_ERROR |
| 동일 상품명 등록 | 409 PRODUCT_NAME_DUPLICATED |

상품명 중복은 기존 DB 유니크 제약으로 방어한다. 저장 시 유니크 위반 SQLSTATE 23505를 감지해 비즈니스 예외로 변환한다. 사전 조회에만 의존하면 동시 요청에서 중복이 발생할 수 있어 DB 제약을 사용했다.

이 변환은 현재 상품 저장 과정에서 발생하는 유니크 위반을 상품명 중복으로 해석한다. 향후 다른 유니크 제약을 추가하면 제약별 분류를 검토한다.

## 5. 브라우저 접근 시 500 오류

### 증상

브라우저에서 http://localhost:8083/api/products 주소를 열었을 때 아래 응답이 반환됐다. 실행 중인 서비스에 GET 요청을 보내 500 응답을 재현했다.

```json
{"success":false,"errorCode":"INTERNAL_ERROR","message":"서버 오류가 발생했습니다","errors":null}
```

### 원인과 수정

브라우저 주소창 접근은 GET 요청이다. 현재 구현한 상품 생성 엔드포인트는 POST를 지원한다.

공통 예외 처리기의 Exception 핸들러가 지원하지 않는 HTTP 메서드 예외까지 INTERNAL_ERROR로 변환하고 있었다. 다음 항목을 추가했다.

- ErrorCode에 METHOD_NOT_ALLOWED 추가
- GlobalExceptionHandler에 HttpRequestMethodNotSupportedException 처리 추가
- 예외의 상태 코드와 Allow 헤더를 보존해 405 응답 반환
- GET 요청에서 405, Allow 헤더의 POST, METHOD_NOT_ALLOWED를 확인하는 회귀 테스트 추가

컨테이너 로그 접근은 작업 세션의 Docker 소켓 권한 때문에 제한됐다. 원인은 소스 확인과 MockMvc 회귀 테스트로 검증했다.

## 6. 검증 결과

상품 생성 구현 직후 product 테스트 17개가 통과했다.

- 기존 상품 도메인 테스트 13개
- 상품 생성과 Outbox 기록·중복 요청 검증 1개
- 소수 가격 거부 검증 1개
- 기존 Outbox Relay 통합 테스트 2개

405 회귀 테스트를 추가한 뒤 common:test와 product:test를 함께 실행해 BUILD SUCCESSFUL을 확인했다.

상품 API 테스트는 H2와 MockMvc를 사용한다. 해당 테스트에서는 Outbox Relay를 비활성화해 저장 결과를 확인하고, 별도의 기존 Relay 통합 테스트가 내장 Kafka로 발행 동작을 검증한다.

이 결과는 실제 PostgreSQL, Compose Kafka, stock, Valkey까지 연결한 종단 간 검증과 구분한다. 실제 컨테이너에서 POST 요청의 201 응답과 stock 반영 결과는 아직 확인 기록이 없다. 동시 중복 등록과 Outbox 저장 실패 시 전체 롤백을 직접 검증하는 테스트도 후속 보강 대상으로 남긴다.

## 7. 컨테이너 반영과 수동 확인

프로젝트 루트에서 상품 이미지를 재빌드하고 컨테이너를 갱신한다.

```zsh
./gradlew :product:jibDockerBuild && docker compose up -d product
```

상품 서비스 기동 완료 후 아래 요청을 보낸다.

```zsh
curl -i -X POST http://localhost:8083/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"테스트 키보드","price":30000}'
```

최초 등록은 201, 같은 이름으로 재등록하면 409가 예상된다. GET으로 같은 주소를 열면 수정된 코드에서는 405가 예상된다.

## 8. 후속 작업과 수정할 항목

- [ ] 이슈 #16 본문의 상세 요구사항과 구현 대조
- [ ] 최신 상품 이미지 반영 후 POST 201, 중복 409, GET 405 확인
- [ ] 상품과 Outbox가 함께 저장되는지 실제 PostgreSQL에서 확인
- [ ] Compose Kafka에서 ProductCreated 발행 확인
- [ ] stock 소비자와 재고 초기화 구현
- [ ] 이벤트 재전달이 기존 재고를 초기화하지 않는지 검증
- [ ] DB 커밋 이후 Valkey 반영 실패와 복구 동작 검증
- [ ] 주문 가능 상태, 이벤트 지연 허용 범위, 가격 검증 정책 정의
- [ ] 주문 재고 예약의 정합성과 부하 측정
- [ ] 최종 실행 결과와 결정 근거를 이 문서에 갱신

## 관련 문서

- [1주차 과제와 작업 계획](week1.md)
- [상품 API 명세](../../api/product.md)
- [재고 API 명세](../../api/stock.md)
- [Outbox 설계](../../outbox.md)
