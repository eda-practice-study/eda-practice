package com.eda.common.outbox;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ComponentScan
@AutoConfigurationPackage
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    @Bean
    public LockProvider lockProvider(
            DataSource dataSource,
            @Value("${spring.jpa.properties.hibernate.default_schema:}") String schema
    ) {
        String table = schema.isBlank() ? "shedlock" : schema + ".shedlock";
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName(table)
                .usingDbTime()
                .build());
    }
}
