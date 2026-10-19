package com.eda.notification;

import com.eda.common.message.MessageHeader;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 알림 발송 대신 받은 이벤트를 로그로 남기는 mock
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final JsonMapper jsonMapper;

    @KafkaListener(topicPattern = ".*\\.events", groupId = "notification-service")
    public void on(ConsumerRecord<String, String> record) {
        log.info("""
                [알림] {} (partition={}, offset={})
                  key        : {}
                  type       : {}
                  messageId  : {}
                  occurredAt : {}
                  payload    :
                {}""",
                record.topic(), record.partition(), record.offset(),
                record.key(),
                header(record, MessageHeader.MESSAGE_TYPE),
                header(record, MessageHeader.MESSAGE_ID),
                header(record, MessageHeader.OCCURRED_AT),
                pretty(record.value()));
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "-" : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String pretty(String payload) {
        if (payload == null) {
            return "-";
        }
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMapper.readTree(payload));
        } catch (JacksonException e) {
            return payload;
        }
    }
}
