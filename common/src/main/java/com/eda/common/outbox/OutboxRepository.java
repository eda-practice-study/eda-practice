package com.eda.common.outbox;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findByStatusOrderByIdAsc(OutboxStatus status, Limit limit);
}
