# 상품 등록

## 현재 동작

구현된 사용자 API는 `POST /api/products`다.
요청과 응답의 형식은 [스터디 API 명세](../../docs/api/product.md)를 참조한다.
같은 문서에 있는 상품 목록 조회는 아직 구현하지 않았다.

1. 컨트롤러가 필수값과 길이 및 가격 범위를 검증한다.
2. `Product.register()`가 상품명과 가격의 도메인 규칙을 확인한다.
3. `ProductService.register()`가 상품명 중복을 조회한다.
4. 상품을 저장하고 같은 트랜잭션에서 `ProductCreated`를 Outbox에 저장한다.
5. 커밋 성공 후 `201 Created`와 `Location: /api/products/{productId}`를 반환한다.
6. product 프로세스 안의 Relay가 [상품 생성 이벤트](../architecture/events.md)를 비동기로 발행한다.

상품명은 필수이고 공백만으로 구성될 수 없으며 최대 100자다.
가격은 1부터 1,000,000까지의 원 단위 정수다. `30000.00`처럼 값이 정수인 표현은 도메인에서 허용한다.
상품 생성 시 초기 재고를 입력받지 않는다.

## 상품명 중복과 오류

- 사전 조회에서 같은 이름이 발견되면 `ProductNameDuplicatedException`을 던진다.
- 동시 요청은 사전 조회를 함께 통과할 수 있으므로 DB의 `uk_product_name` 유일 제약을 유지한다.
- `ProductControllerAdvice`는 상품 컨트롤러에만 적용하며 공통 예외 처리기보다 먼저 해당 DB 제약 위반을 409로 변환한다.
- 다른 무결성 위반은 상품명 중복으로 간주하지 않고 500으로 응답한다.
- 필드 검증 오류와 읽을 수 없는 JSON은 400으로 응답한다.

왜 사전 조회와 DB 예외 처리를 함께 사용하는지는 [상품명 중복 결정](../adr/0006-product-name-uniqueness.md)에 정리했다.

## 완료의 의미와 한계

201 응답은 product DB의 상품과 Outbox가 커밋되었다는 뜻이다.
Kafka 발행, stock의 재고 생성, Redis 초기화, order의 스냅샷 생성까지 완료했다는 뜻은 아니다.
후속 처리 상태를 모아 확인하는 API는 없다.

Outbox의 UUID는 같은 이벤트의 재전달을 식별한다. 상품 등록 HTTP 요청 자체의 재시도를 식별하는 키는 구현하지 않았다.
동일 이름의 재요청은 현재 상품명 중복 정책으로 처리되며 이전 성공 응답을 복원하는 API 멱등성 기능은 아니다.

## 코드와 검증

- [컨트롤러](../../product/src/main/java/com/eda/product/api/ProductController.java)
- [서비스](../../product/src/main/java/com/eda/product/service/ProductService.java)
- [상품명 중복 예외 처리](../../product/src/main/java/com/eda/product/exception/ProductControllerAdvice.java)
- [서비스 단위 테스트](../../product/src/test/java/com/eda/product/service/ProductServiceTest.java)
- [컨트롤러 단위 테스트](../../product/src/test/java/com/eda/product/api/ProductControllerTest.java)
- [Relay 통합 테스트](../../product/src/test/java/com/eda/product/outbox/OutboxRelayIntegrationTest.java)

검증 명령과 DB 확인 방법은 [실행 문서](../runbook.md)를 참조한다.
