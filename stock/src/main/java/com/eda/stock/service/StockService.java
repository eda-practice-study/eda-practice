package com.eda.stock.service;

import com.eda.stock.domain.ProcessedMessage;
import com.eda.stock.domain.ProcessedMessageRepository;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.exception.InvalidProductEventException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final ProcessedMessageRepository processedMessageRepository;

    @Transactional
    public void createFor(UUID messageId, Long productId) {
        if (messageId == null || productId == null || productId <= 0) {
            throw new InvalidProductEventException("messageId와 양수인 productId가 필요합니다");
        }
        if (processedMessageRepository.existsById(messageId)) {
            return;
        }

        processedMessageRepository.saveAndFlush(new ProcessedMessage(messageId));
        if (!stockRepository.existsByProductId(productId)) {
            stockRepository.save(Stock.createFor(productId));
        }
    }
}
