package com.eda.product;

import com.eda.common.exception.GlobalExceptionHandler;
import com.eda.common.outbox.OutboxConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({GlobalExceptionHandler.class, OutboxConfig.class})
@SpringBootApplication
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
