package com.eda.stock.scheduler;

import static org.mockito.Mockito.*;

import com.eda.stock.service.StockReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DirtiesContext
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-reconciliation-scheduler;DB_CLOSE_DELAY=-1",
        "stock.reconciliation.enabled=true",
        "stock.reconciliation.interval=50ms"
})
class StockReconciliationSchedulerIntegrationTest {
    @MockitoBean StockReconciliationService service;

    @Test
    @DisplayName("대사가 활성화되면 시작 후 설정한 간격으로 반복 실행한다")
    void reconcile_shouldRunRepeatedly_whenEnabled() {
        verify(service, timeout(5000).atLeast(2)).reconcile();
    }
}
