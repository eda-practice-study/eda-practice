package com.eda.product.application.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.product.adapter.out.persistence.ProductJpaRepository;
import com.eda.product.adapter.out.persistence.outbox.OutboxEventJpaRepository;
import com.eda.product.application.port.in.RegisterProductUseCase;
import com.eda.product.application.port.in.RegisterProductUseCase.RegisterProductCommand;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 저장과 이벤트 기록이 같은 트랜잭션에 묶였는지 확인한다.
 * 1주차에는 커밋 후 Kafka 로 보냈기 때문에 이 둘을 함께 되돌릴 방법이 없었다.
 */
@SpringBootTest
class ProductCommandServiceTransactionTest {

    @Autowired
    private RegisterProductUseCase registerProductUseCase;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 실제 커밋 여부를 봐야 해서 테스트에 @Transactional 을 걸 수 없다. 직접 비운다.
     */
    @BeforeEach
    void clear() {
        outboxEventJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("커밋되면 상품과 이벤트가 모두 남는다")
    void keepBothOnCommit() {
        // when
        transactionTemplate.executeWithoutResult(status ->
                registerProductUseCase.register(new RegisterProductCommand("티셔츠", BigDecimal.valueOf(10000))));

        // then: 이벤트 레코드가 반드시 남으므로 유실되지 않는다
        assertThat(productJpaRepository.count()).isOne();
        assertThat(outboxEventJpaRepository.count()).isOne();
    }

    @Test
    @DisplayName("롤백되면 상품과 이벤트가 함께 사라진다")
    void discardBothOnRollback() {
        // when
        transactionTemplate.executeWithoutResult(status -> {
            registerProductUseCase.register(new RegisterProductCommand("티셔츠", BigDecimal.valueOf(10000)));
            status.setRollbackOnly();
        });

        // then: 존재하지 않는 상품의 이벤트가 나가지 않는다 (유령 이벤트 방지)
        assertThat(productJpaRepository.count()).isZero();
        assertThat(outboxEventJpaRepository.count()).isZero();
    }
}
