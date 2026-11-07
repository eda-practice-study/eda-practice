package com.eda.order.config;

import com.eda.order.exception.InvalidProductEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class OrderKafkaConfig {

    @Bean
    public DeadLetterPublishingRecoverer orderDeadLetterRecoverer(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition("order.product.events.DLT", -1));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    @Bean
    public DefaultErrorHandler orderKafkaErrorHandler(DeadLetterPublishingRecoverer orderDeadLetterRecoverer) {
        var handler = new DefaultErrorHandler(orderDeadLetterRecoverer, new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(InvalidProductEventException.class);
        return handler;
    }
}
