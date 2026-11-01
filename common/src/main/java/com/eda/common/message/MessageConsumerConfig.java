package com.eda.common.message;

import java.time.format.DateTimeParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration
public class MessageConsumerConfig {

    // 1초 간격으로 3번 재시도한 뒤 로그를 남기고 건너뛴다. 형식이 깨진 메시지는 재시도하지 않는다.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(
                IllegalArgumentException.class, DateTimeParseException.class, JacksonException.class);
        return handler;
    }
}
