package com.eda.order.stock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stock.client")
public record StockClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
