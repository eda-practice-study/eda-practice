# Transactional Outbox

업무 데이터와 같은 트랜잭션에 메시지를 저장해 두고, relay 가 그 메시지를 Kafka 로 발행한다.

## 흐름

1. 서비스 트랜잭션에서 업무 데이터를 저장하고 `OutboxWriter.write(...)` 를 호출한다. `outbox_message` 에 `PENDING` 행이 들어가고, 업무 데이터와 함께 커밋되거나 함께 롤백된다.
2. `OutboxRelay` 가 1초마다 `PENDING` 을 id 순으로 최대 100건 읽는다. ShedLock 으로 서비스당 한 인스턴스에서만 돈다.
3. 한 건씩 Kafka 로 보내고 응답을 최대 5초 기다린다. key 는 `message_key`, value 는 payload JSON, 헤더로 `messageId` · `messageType` · `occurredAt` 이 붙는다.
4. 성공하면 `PUBLISHED` 로 바꾼다. 실패하면 이번 회차를 멈추고, 다음 회차에 그 메시지부터 다시 보낸다.

## 사용법

발행하는 서비스는 `@Import(OutboxConfig.class)` 를 붙이고, `KafkaTemplate` 대신 `OutboxWriter` 만 쓴다.

payload 는 `common` 의 `com.eda.common.message.{발행 서비스}` 패키지에 record 로 정의한다.

```java
package com.eda.common.message.product;

public record ProductCreated(Long productId, String name, long price) { }
```

- `messageType` 은 패키지를 뺀 클래스 이름이므로, 클래스 이름은 패키지가 달라도 겹치면 안 된다.
- 도메인 엔티티를 그대로 넣지 않고 필요한 값만 담는다.

```java
@Transactional
public Product create(...) {
    Product product = productRepository.save(...);
    outboxWriter.write("product.events", String.valueOf(product.getId()), new ProductCreated(...));
    return product;
}
```

| 인자 | 설명 |
|------|------|
| topic | Kafka 토픽 (예: `product.events`) |
| messageKey | 파티션 키. 같은 키는 순서가 유지된다 (예: productId) |
| payload | 메시지 본문 record. JSON 으로 직렬화되어 value 로 나간다 |

- `messageId`(UUID) · `messageType`(payload 클래스 이름) · `occurredAt` 은 `write()` 가 채운다.
- `write()` 는 트랜잭션 안에서만 호출할 수 있다. 밖에서 부르면 `IllegalTransactionStateException`.

받는 쪽은 `Message.from(...)` 으로 헤더와 value 를 한 번에 읽는다.

```java
@KafkaListener(topics = "product.events", groupId = "stock-service")
public void on(ConsumerRecord<String, String> record) {
    Message<ProductCreated> message = Message.from(record, ProductCreated.class, jsonMapper);
}
```

## 설정

```yaml
outbox:
  enabled: true         # false 면 relay 를 끈다 (테스트용)
  interval: 1s          # 회차 사이 간격
  batch-size: 100       # 한 회차에 읽는 PENDING 건수
  send-timeout: 5s      # 한 건의 응답을 기다리는 시간

spring:
  kafka:
    producer:
      acks: all
      properties:
        enable.idempotence: true
        max.block.ms: 3000   # Kafka 장애 시 send() 가 막히는 최대 시간
```
