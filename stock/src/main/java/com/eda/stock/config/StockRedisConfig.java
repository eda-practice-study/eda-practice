package com.eda.stock.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.CommandType;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StockRedisConfig {

    @Bean
    LettuceClientConfigurationBuilderCustomizer stockRedisClientOptions() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .replayFilter(command -> command.getType() == CommandType.INCRBY)
                .build());
    }
}
