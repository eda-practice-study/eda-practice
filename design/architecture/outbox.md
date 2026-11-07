# Outbox 저장과 발행

## 서비스 트랜잭션

product는 상품과 Outbox를 함께 저장한다. stock과 order는 처리 이력, 업무 데이터와 Outbox를 함께 저장한다.
한 테이블이라도 저장이나 커밋에 실패하면 해당 서비스의 트랜잭션 전체를 롤백한다.
서비스 사이의 트랜잭션은 서로 독립적이다.

`OutboxWriter.write(topic, messageKey, payload)`는 `Propagation.MANDATORY`를 사용하므로 기존 트랜잭션 안에서 호출해야 한다.
UUID, 타입, 발생 시각과 JSON 본문을 `PENDING`으로 저장한다.
리스너가 서비스의 커밋 이후에 Outbox를 별도로 저장하는 구조로 변경하지 않는다.
선택 근거는 [Outbox 결정](../adr/0001-transactional-outbox.md)에 있다.

## Relay 실행 구조

product, stock과 order는 `OutboxConfig`를 import한다.
Relay는 별도 서버가 아니라 각 서비스 프로세스 안에서 실행되는 공통 스케줄러다.
업무 데이터와 Outbox는 각 서비스의 스키마에 있고, ShedLock도 해당 스키마의 테이블을 사용한다.

| 설정 | 현재 기본값 |
|---|---|
| `outbox.enabled` | `true` |
| `outbox.interval` | `1s` |
| `outbox.batch-size` | `100` |
| `outbox.send-timeout` | `5s` |
| ShedLock `lockAtMostFor` | 코드에 지정된 `PT1M` |

1. `PENDING`을 ID 오름차순으로 최대 batch-size만큼 읽는다.
2. 각 메시지를 Kafka에 보내고 결과를 기다린다. 저장된 UUID와 타입 및 발생 시각을 헤더에 넣는다.
3. 성공하면 `PUBLISHED`와 발행 시각을 저장한다.
4. 전송 실패 시 이번 회차를 중단한다. 해당 메시지는 `PENDING`으로 남아 다음 회차에 재시도한다.

생산자 설정은 `acks=all`, `enable.idempotence=true`를 사용한다.
이 설정이 DB 상태 변경까지 포함한 단 한 번의 발행을 보장하지는 않는다.

## 감수한 위험과 검토할 부분

- Kafka 발행 후 DB 상태 갱신 전에 종료되면 같은 메시지를 다시 보낸다. 소비자는 저장된 messageId로 중복을 처리해야 한다.
- 먼저 있는 메시지가 계속 실패하면 뒤 메시지도 대기한다. Relay에는 소비자와 같은 유한 재시도 후 DLT 이동 정책이 없다.
- ShedLock의 중복 실행 방지는 락 유효기간 안에서만 기대할 수 있다. 배치 실행이 1분을 넘으면 다른 인스턴스가 락을 얻을 수 있다.
  최대 100건을 순차 전송하고 각 전송 결과를 최대 5초 기다리므로 배치 실행이 1분 안에 끝난다고 보장할 수 없다.
  이는 코드 설정에서 확인한 검토 사항이며 다중 인스턴스에서 재현 검증한 결과는 아니다. 락 연장이나 배치 제한은 아직 결정하지 않았다.
- 발행 완료 Outbox와 처리 이력의 보관 및 정리 정책은 아직 없다.
- 현재는 Hibernate 스키마 생성 정책을 사용한다. 운영 마이그레이션 절차는 별도 구현이 필요하다.

관련 구현: [OutboxWriter](../../common/src/main/java/com/eda/common/outbox/OutboxWriter.java),
[OutboxRelay](../../common/src/main/java/com/eda/common/outbox/OutboxRelay.java),
[OutboxConfig](../../common/src/main/java/com/eda/common/outbox/OutboxConfig.java).
스터디 참고 문서: [기존 Outbox 설명](../../docs/outbox.md).
