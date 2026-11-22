package com.eda.order.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 큐가 길수록 마지막 restore 가 늦어진다. (큐 ÷ 스레드) × 작업당 최악 시간이 stock 의 주문 기록 TTL 보다 짧아야 한다.
@Configuration
@EnableAsync
@EnableResilientMethods
public class StockRestoreConfig {

    @Bean
    public TaskExecutor restoreExecutor(
            @Value("${stock.restore.pool-size}") int poolSize,
            @Value("${stock.restore.queue-capacity}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("stock-restore-");
        executor.initialize();
        return executor;
    }
}
