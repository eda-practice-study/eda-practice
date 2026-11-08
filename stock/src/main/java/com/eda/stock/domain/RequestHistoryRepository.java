package com.eda.stock.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestHistoryRepository extends JpaRepository<RequestHistory, RequestHistoryId> {
}
