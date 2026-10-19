package com.eda.common.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1s") Duration interval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("5s") Duration sendTimeout
) {
}
