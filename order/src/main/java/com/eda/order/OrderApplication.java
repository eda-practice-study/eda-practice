package com.eda.order;

import com.eda.common.exception.GlobalExceptionHandler;
import com.eda.common.message.MessageConsumerConfig;
import com.eda.common.outbox.OutboxConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({GlobalExceptionHandler.class, OutboxConfig.class, MessageConsumerConfig.class})
@SpringBootApplication
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
