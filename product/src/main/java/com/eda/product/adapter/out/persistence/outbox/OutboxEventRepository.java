package com.eda.product.adapter.out.persistence.outbox;

import com.eda.product.domain.outbox.OutboxEvent;
import com.eda.product.domain.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findAllByStatusOrderByOccurredAtAsc(OutboxStatus status);

}
