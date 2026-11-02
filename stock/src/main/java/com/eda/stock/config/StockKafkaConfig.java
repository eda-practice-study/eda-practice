package com.eda.stock.config;

import com.eda.stock.exception.InvalidProductEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class StockKafkaConfig {

    @Bean
    public DeadLetterPublishingRecoverer stockDeadLetterRecoverer(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition("stock.product.events.DLT", -1));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    @Bean
    public DefaultErrorHandler stockKafkaErrorHandler(DeadLetterPublishingRecoverer stockDeadLetterRecoverer) {
        var handler = new DefaultErrorHandler(stockDeadLetterRecoverer, new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(InvalidProductEventException.class);
        return handler;
    }
}
