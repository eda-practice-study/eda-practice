package com.eda.stock.application;

import com.eda.common.message.stock.StockCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.api.dto.IncreaseStockResponse;
import com.eda.stock.application.dto.StockIncreaseResult;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockRedisRepository stockRedisRepository;
    private final OutboxWriter outboxWriter;
    private final StockCommandService stockCommandService;

    @Transactional
    public void register(Long productId) {

        // 중복 발행 확인
        if (stockRepository.existsByProductId(productId)) {
            return;
        }

        // 재고 등록 (DB)
        Stock stock = Stock.createFor(productId);
        stockRepository.save(stock);

        // (Redis)
        stockRedisRepository.initialize(productId);

        // 이벤트 발행
        outboxWriter.write(
                "stock.events",
                stock.getProductId().toString(),
                new StockCreated(stock.getProductId(), stock.getQuantity())
        );
    }

    public IncreaseStockResponse increaseStock(Long productId, int quantity) {
        // 별도 빈 호출이 정상 반환되면 DB 재고와 Outbox 커밋이 완료된 상태다.
        StockIncreaseResult result = stockCommandService.increase(productId, quantity);

        Integer availableQuantity;
        try {
            Long redisQuantity = stockRedisRepository.increase(productId, quantity);
            availableQuantity = redisQuantity == null ? null : Math.toIntExact(redisQuantity);
        } catch (DataAccessException | ArithmeticException e) {
            // Redis 장애 또는 응답 타입 범위 초과는 null로 표현한다. DB 커밋은 유지된다.
            // 타임아웃이어도 Redis에 반영됐을 수 있으므로 무조건 재시도하지 않는다.
            log.warn("Redis 재고 증가 결과 확인 실패 productId={}, quantity={}", productId, quantity, e);
            availableQuantity = null;
        }

        return IncreaseStockResponse.from(result.productId(), result.quantity(), availableQuantity);
    }
}
