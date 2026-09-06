package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.LoadStockOrderDeductionPort;
import com.eda.stock.application.port.out.SaveStockOrderDeductionPort;
import com.eda.stock.domain.StockOrderDeduction;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class StockOrderDeductionPersistenceAdapter
        implements LoadStockOrderDeductionPort, SaveStockOrderDeductionPort {

    private final StockOrderDeductionJpaRepository deductionRepository;

    @Override
    public Optional<StockOrderDeduction> findByOrderId(Long orderId) {
        return deductionRepository.findByOrderId(orderId);
    }

    @Override
    public StockOrderDeduction save(StockOrderDeduction deduction) {
        return deductionRepository.save(deduction);
    }
}
