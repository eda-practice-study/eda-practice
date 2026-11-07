# 실행과 확인 절차

## 다음 세션 시작

저장소 루트에서 아래 명령으로 브랜치와 기존 변경을 먼저 확인한다.

```bash
git branch --show-current
git status --short
git log -5 --oneline
```

기존 작업은 `jangikhwan` 브랜치에서 진행했다. 다음 작업의 브랜치는 사용자 지시를 따른다.
미커밋 파일을 기존 작업의 일부로 간주해 덮어쓰거나 새 커밋에 함께 넣지 않는다.
시점마다 달라지는 작업 트리 목록을 이 문서에 고정해서 기록하지 않는다.

## 실행 환경

Java 도구 체인은 21, Gradle Wrapper는 9.5.1이다. PostgreSQL과 Valkey 통합 테스트에는 실행 중인 Docker가 필요하다.
개발 인프라는 [docker-compose.yml](../docker-compose.yml)을 사용한다.

```bash
docker compose up -d postgres redis kafka kafka-ui
```

애플리케이션을 로컬에서 실행하려면 각 명령을 별도 터미널에서 실행한다.

```bash
./gradlew :product:bootRun
./gradlew :stock:bootRun
./gradlew :order:bootRun
./gradlew :notification:bootRun
```

같은 포트를 사용하는 애플리케이션 컨테이너와 로컬 서버를 동시에 실행하지 않는다.
컨테이너로 실행할 때는 이미지를 빌드한 뒤 서비스를 올린다. 소스 수정만으로 기존 이미지가 바뀌지는 않는다.

```bash
./gradlew :product:jibDockerBuild :stock:jibDockerBuild :order:jibDockerBuild :notification:jibDockerBuild
docker compose up -d product stock order notification
```

| 대상 | 호스트에서 접근 | 애플리케이션 컨테이너에서 접근 |
|---|---|---|
| PostgreSQL | `localhost:5432` | `postgres:5432` |
| Redis 호환 Valkey | `localhost:6379` | `redis:6379` |
| Kafka | `localhost:9092` | `kafka:29092` |
| Kafka UI | `http://localhost:8090` | 호스트 브라우저에서 확인 |
| product / stock / order | 8083 / 8084 / 8081 | 서비스 이름과 같은 포트 |
| notification | 8085 | 로그용 이벤트 소비자 |

Kafka 토픽은 개발 환경에서 자동 생성한다. 자동 생성을 끄면 [이벤트 계약](architecture/events.md)에 나오는 업무 토픽과 DLT를 준비한다.

## DB 접속

현재는 하나의 PostgreSQL DB 안에서 서비스별 스키마를 구분한다.

| 항목 | 설정 |
|---|---|
| Database | `eda` |
| Username / Password | `eda` / `eda` |
| product 스키마 | `product` |
| stock 스키마 | `stock` |
| order 스키마 | `orders` |

product의 호스트 접속 URL 예시는 `jdbc:postgresql://localhost:5432/eda?currentSchema=product`다.
`currentSchema`는 옵션 이름이고 `product`가 스키마 이름이다.
스키마와 테이블은 현재 각 애플리케이션의 Hibernate 설정으로 생성한다.

```bash
docker compose exec postgres psql -U eda -d eda
```

## 상품 생성 흐름 확인

```bash
curl -i -X POST http://localhost:8083/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"인수인계 확인 상품","price":30000}'
```

응답의 productId를 사용한다. 같은 이름으로 다시 요청하면 현재 정책상 409이며 새 상품이 추가되지 않는다.
201 직후 후속 서버의 처리까지 완료됐다고 가정하지 않고 최종 저장 상태를 확인한다.

psql에서 아래 `1`을 응답의 상품 ID로 바꾼다.

```sql
\set product_id 1
SELECT id, name, price FROM product.product WHERE id = :product_id;
SELECT product_id, quantity FROM stock.stock WHERE product_id = :product_id;
SELECT product_id, name, price FROM orders.product_snapshot WHERE product_id = :product_id;

SELECT message_id, topic, message_type, status
FROM product.outbox_message WHERE message_key = :'product_id';
SELECT message_id, topic, message_type, status
FROM stock.outbox_message WHERE message_key = :'product_id';
SELECT message_id, topic, message_type, status
FROM orders.outbox_message WHERE message_key = :'product_id';

SELECT p.message_id,
       s.message_id IS NOT NULL AS stock_processed,
       o.message_id IS NOT NULL AS order_processed
FROM product.outbox_message p
LEFT JOIN stock.processed_message s ON s.message_id = p.message_id
LEFT JOIN orders.processed_message o ON o.message_id = p.message_id
WHERE p.message_key = :'product_id' AND p.message_type = 'ProductCreated';
```

정상적인 최초 생성에서는 Stock 수량이 0이고 스냅샷의 이름과 가격은 상품 입력값과 같다.
각 서비스 Outbox의 발행 완료 상태는 `PUBLISHED`이며, 발행 지연이나 장애 중에는 `PENDING`일 수 있다.
stock과 order의 처리 이력은 입력 `ProductCreated`의 UUID를 사용하고, 후속 Outbox는 각각 새로운 UUID를 사용한다.

Redis는 실제 상품 ID를 넣어 확인한다.

```bash
docker compose exec redis valkey-cli GET stock:available:1
```

정상적인 최초 초기값은 문자열 `0`이다. 키가 없더라도 DB 커밋은 성공했을 수 있다.
이벤트를 다시 보내거나 Redis에 무조건 0을 기록하는 방법을 복구 절차로 사용하지 않는다.
이유는 [Redis 초기화 결정](adr/0004-stock-redis-initialization.md)을 참조한다.

Kafka UI에서는 다음 토픽의 key와 본문 및 헤더를 확인한다.

- `product.events`: `ProductCreated`
- `stock.events`: `StockCreated`
- `order.events`: `ProductSnapshotCreated`

notification 로그도 수신 사실을 확인하는 보조 수단이다. 로그만으로 DB 업무 성공이나 중복 없는 알림 처리를 입증하지는 않는다.

## 재고 증가 확인

재고가 이미 생성된 상품 ID를 사용한다. 한 번의 증가 의도에는 같은 키를 사용하고,
새 증가 요청에는 새 키를 사용한다.

```bash
curl -i -X POST http://localhost:8084/api/stocks/1/increase \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: stock-increase-example-1' \
  -d '{"quantity":100}'
```

같은 키를 재전송하면 요청 내용에 관계없이 `409 DUPLICATE_STOCK_INCREASE_REQUEST`를 반환하고 재고와 이벤트는 추가하지 않는다.
Redis 호출이 실패하면 DB 수량은 증가하고 `availableQuantity`는 `null`이다.
중복 키 오류는 DB 증가가 이미 커밋됐다는 뜻이며 Redis 반영 여부나 최초 응답 수신 여부를 알려주지 않는다.
응답을 받지 못했다는 이유로 새 키를 사용하면 다시 증가할 수 있다. 현재 자동 재처리나 대사는 없다.

```sql
SELECT product_id, quantity, version FROM stock.stock WHERE product_id = 1;
SELECT idempotency_key, product_id, created_at
FROM stock.stock_increase_request WHERE idempotency_key = 'stock-increase-example-1';
SELECT message_type, payload, status FROM stock.outbox_message
WHERE message_key = '1' AND message_type = 'StockIncreased';
```

현재 개발 환경에서는 Hibernate `ddl-auto=update`로 버전 컬럼과 멱등성 이력 테이블을 생성한다.
버전 컬럼은 기본값 0과 NOT NULL을 지정한다. 이전에 nullable 버전 컬럼을 만든 환경은
애플리케이션을 중지한 상태에서 NULL 값을 0으로 보정하고 제약을 적용한 뒤 재시작한다.
운영용 마이그레이션 도구는 아직 없다.

```sql
ALTER TABLE stock.stock ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
UPDATE stock.stock SET version = 0 WHERE version IS NULL;
ALTER TABLE stock.stock ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE stock.stock ALTER COLUMN version SET NOT NULL;
```

이전 응답 저장 방식의 스키마가 있다면, 애플리케이션을 중지하고 아래 컬럼을 제거한 뒤 새 버전을 실행한다.
Hibernate `ddl-auto=update`는 삭제된 필드를 테이블에서 자동 제거하지 않으므로,
기존 NOT NULL 컬럼이 남으면 새 요청 이력 INSERT가 실패할 수 있다.
기존 멱등성 키와 상품 ID는 유지하며 이력 자체를 삭제하지 않는다. 아래 변경은 자동 실행되지 않는다.

```sql
ALTER TABLE stock.stock_increase_request
    DROP COLUMN IF EXISTS increased_quantity,
    DROP COLUMN IF EXISTS quantity,
    DROP COLUMN IF EXISTS available_quantity,
    DROP COLUMN IF EXISTS completed;
```

## 실패 위치 확인

| 관측한 상태 | 확인할 부분 |
|---|---|
| product Outbox가 PENDING으로 남음 | product Relay 로그와 Kafka 연결 |
| product 발행 완료, stock 또는 order 데이터 없음 | 해당 소비자의 로그, 그룹 위치와 전용 DLT |
| stock DB는 있으나 Redis 키 없음 | stock의 Redis 실패 로그와 연결. 현재 자동 복구 기능 없음 |
| stock 또는 order DB는 있으나 후속 이벤트를 못 봄 | 해당 서비스 Outbox 상태와 Relay 로그 |
| 관계나 테이블이 없다는 SQL 오류 | 해당 서비스의 최초 부팅과 Hibernate 스키마 생성 로그 |

DLT로 이동한 메시지는 업무 처리에 실패한 것이다. DLT 조회와 재처리 API는 아직 없다.
재처리할 때 원본 messageId와 업무 상태를 확인해야 하며, 처리 이력이나 재고를 임의로 삭제하는 절차는 정해두지 않았다.
ShedLock 유효기간을 넘는 배치 실행에 관한 한계는 [Relay 문서](architecture/outbox.md)를 참조한다.

SQL 출력은 서비스별 `spring.jpa.show-sql`과 로거 설정에 따라 다르다.
로컬 설정과 실행 환경변수 및 이미지에 포함된 설정을 구분해서 확인한다.

## 자동 테스트와 검증 기록

```bash
./gradlew :common:test :product:test :stock:test :order:test
```

빠른 확인은 해당 모듈과 테스트 클래스를 지정한다.

```bash
./gradlew :order:test --tests com.eda.order.service.ProductSnapshotServiceTest
./gradlew :stock:test :order:test --tests '*ProductEventListenerIntegrationTest' --tests '*KafkaConfigTest'
```

PostgreSQL 17과 Valkey 통합 테스트에는 Docker가 필요하고 Kafka는 Embedded Kafka를 사용한다.
컨테이너 테스트는 Docker가 없다는 이유로 자동으로 건너뛰지 않는다.
검증 결과는 실행한 명령과 작업 트리 기준으로 해석한다. 예외를 던지는 단위 테스트와 실제 DB 롤백 통합 테스트는 서로 다른 증거다.

2026-11-07 문서화 이전 작업에서는 위 네 모듈 전체 테스트와 후속 Kafka 설정 및 통합 테스트 재검증이 성공했다.
이 기록은 이후의 미커밋 변경이나 문서화 이후 코드까지 검증했다는 뜻이 아니다.
이번 문서 정리에서는 상대 링크와 문서 구조 및 Git 변경 범위를 검증하며 애플리케이션 테스트는 재실행하지 않는다.

2026-11-08 재고 증가 API 작업에서는 `./gradlew :stock:test :common:test :product:test`가 통과했다.
추가 통합 테스트는 PostgreSQL의 증가 트랜잭션 롤백과 낙관적 락 충돌,
Valkey의 실제 증가와 응답 유실 모사, Embedded Kafka의 `StockIncreased` 발행을 확인한다.
네트워크 응답 유실은 테스트에서 증가 명령 실행 후 타임아웃 예외를 주입해 모사하며,
실제 네트워크 단절이나 Redis 데이터 유실 복구를 검증한 것은 아니다.
