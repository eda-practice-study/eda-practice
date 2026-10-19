package com.eda.common.outbox;

import com.eda.common.message.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage write(String topic, String messageKey, Object payload) {
        Message<Object> message = Message.of(payload);
        return outboxRepository.save(
                OutboxMessage.create(topic, messageKey, message, jsonMapper.writeValueAsString(payload)));
    }
}
